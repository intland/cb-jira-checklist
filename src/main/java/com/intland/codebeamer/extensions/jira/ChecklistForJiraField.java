/*
 * Copyright by Intland Software
 *
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Intland Software. ("Confidential Information"). You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with Intland.
 */
package com.intland.codebeamer.extensions.jira;

import static com.intland.codebeamer.extensions.jira.ChecklistForJiraMarkup.cb2checklist;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraMarkup.checklist2cb;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PINNED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.unwrapChecklist;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.wrapChecklist;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.controller.AbstractJsonController;
import com.intland.codebeamer.controller.jira.CustomField;
import com.intland.codebeamer.controller.jira.JiraImportController;
import com.intland.codebeamer.controller.jira.JiraTrackerSyncConfig;
import com.intland.codebeamer.manager.util.ImportStatistics;
import com.intland.codebeamer.manager.util.ImporterSupport;
import com.intland.codebeamer.manager.util.Numbers;
import com.intland.codebeamer.manager.util.TrackerItemDiff;
import com.intland.codebeamer.manager.util.TrackerItemHistoryConfiguration;
import com.intland.codebeamer.manager.util.TrackerItemNumbers;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.TrackerItemRevisionDto;
import com.intland.codebeamer.persistence.dto.TrackerLayoutLabelDto;
import com.intland.codebeamer.wiki.plugins.ChecklistPlugin;

/**
 * A JIRA Connector Plugin to import/export <a href=
 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
 * for JIRA<a> custom fields from/to Atlassian JIRA into/from
 * {@link ChecklistPlugin} Wiki fields
 * 
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Component("com.okapya.jira.checklist:checklist")
@CustomField(type = "WikiText", of = "Checklist")
public class ChecklistForJiraField extends AbstractJsonController {
	private static final Logger logger = Logger.getLogger(ChecklistForJiraField.class);

	public static final String RANK = "rank";
	public static final String IS_HEADER = "isHeader";
	public static final String OPTION = "option"; // ChecklistForJira V4 and older
	public static final String GLOBAL_ID = "globalItemId"; // ChecklistForJira V5 and newer
	public static final String NONE = "none";
	public static final String DESC_SEP = "\n>>";

	/**
	 * A wrapper around a JSON array of checklist items
	 */
	public static class Checklist {
		private ArrayNode items;

		public Checklist(JsonNode items) {
			this.items = (items instanceof ArrayNode ? (ArrayNode) items : jsonMapper.createArrayNode());
		}

		public ArrayNode getItems() {
			return items;
		}

		public int size() {
			return items.size();
		}

		public ObjectNode addItem() {
			return items.addObject();
		}

		public ObjectNode getItem(String name) {
			if (items.size() > 0 && name != null) {
				for (JsonNode item : items) {
					if (item instanceof ObjectNode && name.equals(getString(item, NAME))) {
						return (ObjectNode) item;
					}
				}
			}

			return null;
		}

		public ObjectNode removeItem(String name) {
			ObjectNode result = null;

			if (items.size() > 0) {
				for (Iterator<JsonNode> it = items.iterator(); it.hasNext();) {
					JsonNode item = it.next();
					if (item == null || !item.isObject()) {
						it.remove();
					} else if (name != null && name.equals(getString(item, NAME))) {
						it.remove();

						if (item instanceof ObjectNode) {
							result = (ObjectNode) item;
						}

						break;
					}
				}
			}
			return result;
		}

		public Checklist reorderItems() {
			if (items != null && items.size() > 1) {
				ArrayNode reordered = jsonMapper.createArrayNode();

				// Put all options (in original order) on top
				for (Iterator<JsonNode> it = items.iterator(); it.hasNext();) {
					JsonNode item = it.next();
					if (item != null && item.isObject() && getBoolean(item, PINNED)) {
						reordered.add(item);
						it.remove();
					}
				}

				// Remaining items are issue specific items, that should be reordered
				if (items.size() > 1) {
					// We don't know, how to reorder the items, so we simply swap the two last items
					// Collections.swap(items, items.size() - 2, items.size() - 1);
				}

				reordered.addAll(items);

				items = reordered;
			}

			return this;
		}

		/**
		 * Apply the order of the specified checklist to our checklist items
		 * 
		 * @param ordered is a checklist whose item order to apply
		 * @return this table with applied order
		 */
		public Checklist applyOrder(JsonNode ordered) {
			if (ordered != null && ordered.isArray() && ordered.size() > 0 && ordered != items && size() > 1) {
				ArrayNode reordered = jsonMapper.createArrayNode();

				for (JsonNode item : ordered) {
					JsonNode node = removeItem(getString(item, NAME));
					if (node != null) {
						reordered.add(item);
					}
				}

				// Remaining items have no defined order, so they are added in current order to
				// the end of the ordered list
				reordered.addAll(items);

				items = reordered;
			}

			return this;
		}
	}

	/**
	 * According to <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a> the default value (options) of a checklist, are available in the
	 * "allowedValues" property. Only status ID is available with Meta API's. The
	 * status name is not. Since the "allowedValues" of a JIRA field are
	 * automatically converted into field choice options in codeBeamer, we must
	 * register the additional checklist item properties {@link #IS_HEADER},
	 * {@link #OPTION}, {@link #MANDATORY} and {@link #CHECKED} to be converted into
	 * appropriate choice option flags
	 */
//	static {
//		JiraImportController.addOptionFlag(Flag.Folder, 	 IS_HEADER);
//		JiraImportController.addOptionFlag(Flag.Information, OPTION);
//		JiraImportController.addOptionFlag(Flag.Successful,  MANDATORY);
//		JiraImportController.addOptionFlag(Flag.Resolved, 	 CHECKED);
//	}

	public static String check4ByteChars(JiraImportController controller, String string) {
		return controller != null ? controller.check4ByteChars(string) : string;
	}

	/**
	 * Convert a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a> into a {@link ChecklistPlugin} body
	 * 
	 * @param tracker    is the JIRA tracker sync configuration
	 * @param checklist  is the checklist as returned from Jira
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}
	 * @return the checklist converted into a {@link ChecklistPlugin} body
	 */
	public JsonNode jira2cb(JiraTrackerSyncConfig tracker, JsonNode checklist, JiraImportController controller) {
		if (checklist != null && checklist.isArray() && checklist.size() > 0) {
			for (JsonNode item : checklist) {
				if (item != null && item.isObject()) {
					ObjectNode itemNode = (ObjectNode) item;

					// We don't need the rank, and it's read-only anyways
					itemNode.remove(RANK);

					if (getBoolean(itemNode.remove(IS_HEADER), null)) {
						itemNode.set(HEADER, BooleanNode.TRUE);
					}

					// In Checklist for JIRA V5.0, OPTION is deprecated and replaced by OPTION_ID
					if (getInteger(item, GLOBAL_ID) != null || getBoolean(itemNode.remove(OPTION), null)) {
						itemNode.set(PINNED, BooleanNode.TRUE);
					}

					String name = checklist2cb(check4ByteChars(controller, getString(item, NAME)));
					if (StringUtils.isNotBlank(name)) {
						String desc = null;

						// Split name into name and description, according to
						// <a
						// href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1965752414/Adding+descriptions+to+items+or+headers">Item
						// descriptions<a>
						int descSepIdx = name.indexOf(DESC_SEP);
						if (descSepIdx >= 0) {
							desc = StringUtils.trimToNull(name.substring(descSepIdx + DESC_SEP.length()));
							name = StringUtils.trimToNull(name.substring(0, descSepIdx));
						}

						if (name != null) {
							itemNode.set(NAME, TextNode.valueOf(name));
						} else {
							itemNode.remove(NAME);
						}

						if (desc != null) {
							itemNode.set(DESCRIPTION, TextNode.valueOf(desc));
						} else {
							itemNode.remove(DESCRIPTION);
						}
					}
				}
			}
		}

		return checklist;
	}

	/**
	 * Convert a {@link ChecklistPlugin} body into a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a>
	 * 
	 * @param tracker   is the JIRA tracker sync configuration
	 * @param checklist is the {@link ChecklistPlugin} body
	 * @return the converted {@link ChecklistPlugin} body
	 */
	public JsonNode cb2jira(JiraTrackerSyncConfig tracker, JsonNode checklist) {
		if (checklist != null && checklist.isArray() && checklist.size() > 0) {
			for (JsonNode item : checklist) {
				if (item != null && item.isObject()) {
					ObjectNode itemNode = (ObjectNode) item;

					if (getBoolean(itemNode.remove(HEADER), null)) {
						itemNode.set(IS_HEADER, BooleanNode.TRUE);
					}

					// In Checklist for JIRA V5.0, OPTION is deprecated and replaced by OPTION_ID
					if (getBoolean(itemNode.remove(PINNED), null) && getInteger(item, GLOBAL_ID) == null) {
						itemNode.set(OPTION, BooleanNode.TRUE);
					}

					// Concat name and description, according to
					// <a
					// href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1965752414/Adding+descriptions+to+items+or+headers">Item
					// descriptions<a>
					String name = getString(item, NAME);
					String desc = getString(itemNode.remove(DESCRIPTION), null);
					if (desc != null) {
						name = StringUtils.defaultString(name) + DESC_SEP + "\n" + desc;
					}

					itemNode.set(NAME, TextNode.valueOf(cb2checklist(name)));
				}
			}
		}

		return checklist;
	}

	/**
	 * Configure a codeBeamer Wikitext field to contain a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a>
	 * 
	 * @param field      is the Wikitext field to configure
	 * @param metaData   is the JIRA checklist field meta data
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}
	 */
	@CustomField.MetaData
	public void setMetaData(TrackerLayoutLabelDto field, ObjectNode metaData, JiraImportController controller) {
		// In the JIRA REST-API schema, a checklist field is an array of checklist
		// items, but in CB it's a single value WIKI field
		field.setMultipleSelection(Boolean.FALSE);

		// The default value (options) of a checklist, are available in the
		// "allowedValues" property. Only status ID is available with Meta API's. The
		// status name is not.
		field.setDefaultValue(importChecklist(null, metaData.remove("allowedValues"), controller));

		// Place checklist on own row with full width
		field.setBreakRow(Boolean.TRUE);
		field.setColspan(Integer.valueOf(3));
	}

	/**
	 * Convert the specified checklist into {@link ChecklistPlugin} markup
	 * 
	 * @param tracker    is the JIRA tracker sync configuration
	 * @param checklist  should be a JSON array of checklist items to convert into
	 *                   {@link ChecklistPlugin} markup
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}, or
	 *                   null
	 * @return the {@link ChecklistPlugin} markup for the checklist
	 */
	@CustomField.ImportFieldValue
	public String importChecklist(JiraTrackerSyncConfig tracker, JsonNode checklist, JiraImportController controller) {
		return wrapChecklist(jira2cb(tracker, checklist, controller));
	}

	/**
	 * Unwrap the checklist, that is stored in the specified Wiki markup
	 * 
	 * @param tracker is the JIRA tracker sync configuration
	 * @param markup  should be WIKI markup for this plugin
	 * @return the JSON array of checklist items as stored in the Wiki markup
	 */
	@CustomField.ExportFieldValue
	public JsonNode exportChecklist(JiraTrackerSyncConfig tracker, String markup) {
		return cb2jira(tracker, unwrapChecklist(markup));
	}

	/**
	 * Find the original item (before the update), if the specified item is an
	 * update of an existing item
	 * 
	 * @param item      is a newly imported or updated tracker item
	 * @param statistic are the import statistics, where to look for an update of
	 *                  the specified item
	 * @return the original item (before the update), if the item is an
	 *         updated/existing item, or null, if the item is a newly imported item
	 */
	private static TrackerItemDto getOriginalItem(TrackerItemDto item, ImportStatistics statistic) {
		if (item != null && item.getId() != null && statistic != null) {
			Numbers numbers = statistic.get(JiraTrackerSyncConfig.ISSUES);
			if (numbers instanceof TrackerItemNumbers) {
				List<TrackerItemDiff> updatedItems = ((TrackerItemNumbers) numbers).getUpdatedItems();
				if (updatedItems != null && updatedItems.size() > 0) {
					for (TrackerItemDiff updated : updatedItems) {
						if (item.getId().equals(updated.getId())) {
							return updated.getOriginal();
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * Get the {@link ChecklistPlugin} body, that is stored in the specified field
	 * of the specified item
	 * 
	 * @param tracker is the tracker sync configuration
	 * @param item    is the tracker item, that contains the checklist field value,
	 *                or null, to use field default value
	 * @param field   is the Wiki field, that contains {@link ChecklistPlugin}
	 *                markup
	 * @return the {@link ChecklistPlugin} body, that is stored in the specified
	 *         field of the specified item, or null
	 */
	public static JsonNode getChecklist(JiraTrackerSyncConfig tracker, TrackerItemDto item,
			TrackerLayoutLabelDto field) {
		JsonNode checklist = null;

		if (field != null && field.isWikiTextField()) {
			if ((checklist = unwrapChecklist((String) field.getValue(item))) == null) {
				checklist = unwrapChecklist(tracker.getFieldDefaultValue(field, null));
			}
		}

		return checklist;
	}

	/*
	 * Importing checklist history is not supported.
	 * Pretend the new value was always present.
	 * History cleanup will take care of the rest.
	 * Checklist change will show up as first revision imported currently.
	 */
	@CustomField.ImportFieldChange
	public void buildHistory(TrackerItemDto item, TrackerLayoutLabelDto field,
			TrackerItemHistoryConfiguration fieldChange, ImportStatistics statistics, ImporterSupport importer) {
		Object newValue = field.getValue(item);
		fieldChange.setOldValueObject(newValue);
		fieldChange.setNewValueObject(newValue);

	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

}
