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
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.STYLE;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.MANDATORY;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PINNED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.unwrapChecklist;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.wrapChecklist;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.CalendarUtils;
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
import com.intland.codebeamer.persistence.dto.TrackerChoiceOptionDto;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.TrackerLayoutLabelDto;
import com.intland.codebeamer.persistence.dto.base.NamedDto;
import com.intland.codebeamer.wiki.plugins.ChecklistPlugin;


/**
 * A JIRA Connector Plugin to import/export <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist for JIRA<a>
 * custom fields from/to Atlassian JIRA into/from {@link ChecklistPlugin} Wiki fields
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Component("com.okapya.jira.checklist:checklist")
@CustomField(type="WikiText", of="Checklist")
public class ChecklistForJiraField extends AbstractJsonController {
	public static final Logger logger = Logger.getLogger(ChecklistForJiraField.class);

	public static final String CHECKLISTS   = ChecklistForJiraField.class.getName();

	public static final String RANK 		= "rank";
	public static final String IS_HEADER 	= "isHeader";
	public static final String OPTION    	= "option"; 		// ChecklistForJira V4 and older
	public static final String GLOBAL_ID    = "globalItemId";	// ChecklistForJira V5 and newer
	public static final String ASSIGNEE_IDS = "assigneeIds";
	public static final String NONE 	 	= "none";
	public static final String DESC_SEP  	= "\n>>";

	/**
	 * Get the {@link ChecklistPlugin} configuration, for a status with the specified name and style
	 * @param name of the status
	 * @param style of status
	 * @return the {@link ChecklistPlugin} configuration, for a status with the specified name and style
	 */
	public static JsonNode getStatus(String name, String style) {
		if (StringUtils.isNotBlank(name)) {
			if (StringUtils.isNotBlank(style)) {
				return jsonMapper.createObjectNode().put(NAME, name).put(STYLE, style);
			}

			return TextNode.valueOf(name);
		}

		return null;
	}

	/**
	 * The parsed information about a JIRA checklist item change from a JIRA issue changelog <code>fromString</code> or <code>toString</code>
	 */
	public static class Change {
		private String		name;
		private String		desc;
		private String		status;
		private String		priority;
		private String		assigneeIds;
		private Date		dueDate;
		private Set<String> changed;

		/** List of different DueDate Formats used in Checklist history */
		public static final List<FastDateFormat> DUE_DATE_FORMATS = Collections.unmodifiableList(Arrays.asList(
			FastDateFormat.getInstance("dd/MMM/yy",   CalendarUtils.getSystemTimeZone(), Locale.ENGLISH),
			FastDateFormat.getInstance("dd/MM/yy",    CalendarUtils.getSystemTimeZone(), Locale.ENGLISH),
			FastDateFormat.getInstance("dd/MMM/yyyy", CalendarUtils.getSystemTimeZone(), Locale.ENGLISH),
			FastDateFormat.getInstance("dd/MM/yyyy",  CalendarUtils.getSystemTimeZone(), Locale.ENGLISH)
		));

		/**
		 * Decode a Checklist history dueDate
		 * @param encoded is the JIRA formatted date/time string
		 * @return the decoded Date
		 */
		public static Date decodeDueDate(String encoded) {
			if (StringUtils.isNotBlank(encoded)) {
				for (FastDateFormat format : DUE_DATE_FORMATS) {
					try {
						return format.parse(encoded);
					} catch(ParseException px) {
						// Ignore
					}
				}
			}

			return null;
		}

		public Change(String spec) {
			if (spec.startsWith("[")) {
				int closeIdx = spec.indexOf("]");
				if (closeIdx > 0) {
					String changes = spec.substring(1, closeIdx);

					spec = spec.substring(closeIdx + 1).trim();

					for (StringTokenizer parser = new StringTokenizer(changes, ","); parser.hasMoreTokens();) {
						addChanged(StringUtils.trimToNull(parser.nextToken()));
					}
				}
			}

			// In Checklist V5 and newer, the header option can be specified as a second [H] block
			if (spec.startsWith("[H]")) {
				spec = spec.substring(3).trim();

				addChanged("H");
			}

			if (spec.startsWith("(")) {
				int closeIdx = spec.indexOf(")");
				if (closeIdx > 0) {
					status = checklist2cb(StringUtils.trimToNull(spec.substring(1, closeIdx)));
					spec = spec.substring(closeIdx + 1).trim();
				}
			}

			if (spec.endsWith("!)")) {
				int startIdx = spec.lastIndexOf("(");
				if (startIdx >= 0) {
					priority = StringUtils.trimToNull(spec.substring(startIdx + 1, spec.length() - 2));
					spec = spec.substring(0, startIdx).trim();
				}
			}

			if (spec.endsWith(")")) {
				int startIdx = spec.lastIndexOf("(");
				if (startIdx >= 0) {
					assigneeIds = StringUtils.trimToNull(spec.substring(startIdx + 1, spec.length() - 1));
					spec = spec.substring(0, startIdx).trim();
				}
			}

			if (spec.endsWith("}")) {
				int startIdx = spec.lastIndexOf("{");
				if (startIdx >= 0) {
					dueDate = decodeDueDate(StringUtils.trimToNull(spec.substring(startIdx + 1, spec.length() - 1)));
					spec = spec.substring(0, startIdx).trim();
				}
			}

			if (StringUtils.isNotBlank(name = checklist2cb(spec))) {
				// Split name into name and description, according to
				// <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1965752414/Adding+descriptions+to+items+or+headers">Item descriptions<a>
				int descSepIdx = name.indexOf(DESC_SEP);
				if (descSepIdx >= 0) {
					desc = StringUtils.trimToNull(name.substring(descSepIdx + DESC_SEP.length()));
					name = StringUtils.trimToNull(name.substring(0, descSepIdx));
				}
			}
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return desc;
		}

		public String getStatus() {
			return status;
		}

		public String getPriority() {
			return priority;
		}

		public Date getDueDate() {
			return dueDate;
		}

		public String getAssigneeIds() {
			return assigneeIds;
		}

		public boolean isHeader() {
			return hasChanged("h");
		}

		protected void addChanged(String change) {
			if (change != null) {
				if (changed == null) {
					changed = new TreeSet<String>();
				}

				changed.add(change.toLowerCase());

				// Adding a new item implicitly also sets the status, priority, dueDate and assignees
				if ("added".equalsIgnoreCase(change)) {
					changed.add("status changed");
					changed.add("priority changed");
					changed.add("due date changed");
					changed.add("assigned");
				}
			}
		}

		public boolean hasChanged(String change) {
			return changed != null && changed.contains(change);
		}

		public boolean wasAdded() {
			return hasChanged("added");
		}

		public boolean wasRenamed() {
			return hasChanged("modified");
		}

		public boolean wasRemoved() {
			return hasChanged("removed");
		}

		public boolean wasReordered() {
			return hasChanged("reordered");
		}

		public void apply(JiraTrackerSyncConfig tracker, ObjectNode item) {
			if (tracker != null && item != null && changed != null) {
				for (String attrib : changed) {
					if ("added".equals(attrib) || "modified".equals(attrib)) {
						if (name != null) {
							item.set(NAME, TextNode.valueOf(name));
						} else {
							item.remove(NAME);
						}

						if (desc != null) {
							item.set(DESCRIPTION, TextNode.valueOf(desc));
						} else {
							item.remove(DESCRIPTION);
						}
					} else if ("h".equals(attrib)) {
						item.set(HEADER, BooleanNode.TRUE);
					} else if (CHECKED.equals(attrib)) {
						item.set(CHECKED, BooleanNode.TRUE);
					} else if ("unchecked".equals(attrib)) {
						item.set(CHECKED, BooleanNode.FALSE);
					} else if (MANDATORY.equals(attrib)) {
						item.set(MANDATORY, BooleanNode.TRUE);
					} else if ("optional".equals(attrib)) {
						item.set(MANDATORY, BooleanNode.FALSE);
					} else if ("assigned".equals(attrib)) {
						ArrayNode assignees = item.putArray(ASSIGNEE_IDS);
						if (assigneeIds != null) {
							for (StringTokenizer parser = new StringTokenizer(assigneeIds, ", "); parser.hasMoreTokens();) {
								String assignee = StringUtils.trimToNull(parser.nextToken());
								if (assignee != null) {
									assignees.add(assignee);
								}
							}
						}
					} else if ("unassigned".equals(attrib)) {
						item.remove(ASSIGNEE_IDS);
					}
				}
			}
		}

		@Override
		public String toString() {
			boolean       first = true;
			StringBuilder buf = new StringBuilder(80);

			if (changed != null && changed.size() > 0) {
				buf.append('[');
				for (String change : changed) {
					if (first) {
						first = false;
					} else {
						buf.append(", ");
					}
					buf.append(change);
				}
				buf.append(']');
			}

			if (status != null) {
				if (first) {
					first = false;
				} else {
					buf.append(' ');
				}
				buf.append('(').append(status).append(')');
			}

			if (StringUtils.isNotBlank(name)) {
				if (first) {
					first = false;
				} else {
					buf.append(' ');
				}

				buf.append(name);
			}

			return buf.toString();
		}
	}

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

				// Remaining items have no defined order, so they are added in current order to the end of the ordered list
				reordered.addAll(items);

				items = reordered;
			}

			return this;
		}
	}


	/**
	 * According to <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist for JIRA<a>
     * the default value (options) of a checklist, are available in the "allowedValues" property. Only status ID is available with Meta API's. The status name is not.
     * Since the "allowedValues" of a JIRA field are automatically converted into field choice options in codeBeamer, we must register the additional checklist item
     * properties {@link #IS_HEADER}, {@link #OPTION}, {@link #MANDATORY} and {@link #CHECKED} to be converted into appropriate choice option flags
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
	 * Convert a <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist for JIRA<a>
	 * into a {@link ChecklistPlugin} body
	 * @param tracker is the JIRA tracker sync configuration
	 * @param checklist is the checklist as returned from Jira
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
						// <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1965752414/Adding+descriptions+to+items+or+headers">Item descriptions<a>
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
	 * Convert a {@link ChecklistPlugin} body into a
	 * <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist for JIRA<a>
	 * @param tracker is the JIRA tracker sync configuration
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
					// <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1965752414/Adding+descriptions+to+items+or+headers">Item descriptions<a>
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
	 * Configure a codeBeamer Wikitext field to contain a
	 * <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist for JIRA<a>
	 * @param field is the Wikitext field to configure
	 * @param metaData is the JIRA checklist field meta data
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}
	 */
	@CustomField.MetaData
	public void setMetaData(TrackerLayoutLabelDto field, ObjectNode metaData, JiraImportController controller) {
		// In the JIRA REST-API schema, a checklist field is an array of checklist items, but in CB it's a single value WIKI field
		field.setMultipleSelection(Boolean.FALSE);

		// The default value (options) of a checklist, are available in the "allowedValues" property. Only status ID is available with Meta API's. The status name is not.
		field.setDefaultValue(importChecklist(null, metaData.remove("allowedValues"), controller));

		// Place checklist on own row with full width
		field.setBreakRow(Boolean.TRUE);
		field.setColspan(Integer.valueOf(3));
	}

	/**
	 * Convert the specified checklist into {@link ChecklistPlugin} markup
	 * @param tracker is the JIRA tracker sync configuration
	 * @param checklist should be a JSON array of checklist items to convert into {@link ChecklistPlugin} markup
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}, or null
	 * @return the {@link ChecklistPlugin} markup for the checklist
	 */
	@CustomField.ImportFieldValue
	public String importChecklist(JiraTrackerSyncConfig tracker, JsonNode checklist, JiraImportController controller) {
		return wrapChecklist(jira2cb(tracker, checklist, controller));
	}

	/**
	 * Unwrap the checklist, that is stored in the specified Wiki markup
	 * @param tracker is the JIRA tracker sync configuration
	 * @param markup should be WIKI markup for this plugin
	 * @return the JSON array of checklist items as stored in the Wiki markup
	 */
	@CustomField.ExportFieldValue
	public JsonNode exportChecklist(JiraTrackerSyncConfig tracker, String markup) {
		return cb2jira(tracker, unwrapChecklist(markup));
	}

	/**
	 * Parse the information about JIRA checklist changes from a JIRA issue changelog <code>fromString</code> or <code>toString</code>
	 * @param lines is a <code>fromString</code> or <code>toString</code>, that can contain multiple lines of checklist changes, one line per changed checklist item
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}, or null
	 * @return a Map of the parsed checklist item changes
	 */
	@CustomField.ImportFieldHistoryValue
	public Map<Integer,Change> getItemChanges(String lines, JiraImportController controller) {
		Map<Integer,Change> result = Collections.emptyMap();

		if (StringUtils.isNotBlank(lines = check4ByteChars(controller, lines))) {
			Integer		  key = null;
			String 		  cmd = null;
			StringBuilder val = new StringBuilder(256);

			result = new TreeMap<Integer,Change>();

			for (StringTokenizer parser = new StringTokenizer(lines, "\n\r"); parser.hasMoreTokens();) {
				String line = parser.nextToken();

				if ((cmd = StringUtils.trimToNull(line)) != null) {
					// Check if the cmd starts with a new change key:  d+)
					for (int i = 0; i < cmd.length(); ++i) {
						if (!Character.isDigit(cmd.charAt(i))) {
							if (i > 0 && cmd.charAt(i) == ')') {
								// If there is a previous open change
								if (key != null && val.length() > 0) {
									// Submit the change with the previous key
									result.put(key, new Change(val.toString()));
									val.delete(0, val.length());
								}

								// Open a new change with the new key
								key = Integer.valueOf(cmd.substring(0, i));
								cmd = StringUtils.trimToNull(cmd.substring(i + 1));
							}
							break;
						}
					}
				}

				if (key != null) {
					if (val.length() > 0) {
						val.append('\n');
						if (cmd != null) {
							val.append(line);
						}
					} else if (cmd != null) {
						val.append(cmd);
					}
				} else if (StringUtils.containsIgnoreCase(line, "items were reordered")) {
					result.put(Integer.valueOf(9999), new Change("[reordered]"));
				}
			}

			// Submit last/open change
			if (key != null && val.length() > 0) {
				result.put(key, new Change(val.toString()));
				val.delete(0, val.length());
			}
		}

		return result;
	}

	/**
	 * Find the original item (before the update), if the specified item is an update of an existing item
	 * @param item is a newly imported or updated tracker item
	 * @param statistic are the import statistics, where to look for an update of the specified item
	 * @return the original item (before the update), if the item is an updated/existing item, or null, if the item is a newly imported item
	 */
	public static TrackerItemDto getOriginalItem(TrackerItemDto item, ImportStatistics statistic) {
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
	 * Get the {@link ChecklistPlugin} body, that is stored in the specified field of the specified item
	 * @param tracker is the tracker sync configuration
	 * @param item is the tracker item, that contains the checklist field value, or null, to use field default value
	 * @param field is the Wiki field, that contains {@link ChecklistPlugin} markup
	 * @return the {@link ChecklistPlugin} body, that is stored in the specified field of the specified item, or null
	 */
	public static JsonNode getChecklist(JiraTrackerSyncConfig tracker, TrackerItemDto item, TrackerLayoutLabelDto field) {
		JsonNode checklist = null;

		if (field != null && field.isWikiTextField()) {
			if ((checklist = unwrapChecklist((String) field.getValue(item))) == null) {
				checklist = unwrapChecklist(tracker.getFieldDefaultValue(field, null));
			}
		}

		return checklist;
	}

	/**
	 * Get the cache of checklists per field associated with the current import
	 * @param importer is the current import support/cache
	 * @param createIfNecessary whether to create the checklists cache, if not setup yet
	 * @return the the cache of checklists per field associated with the current import, or null, if there is no cache
	 */
	public static Map<Integer,JsonNode> getChecklists(ImporterSupport importer, boolean createIfNecessary) {
		Map<Integer,JsonNode> checklists = (importer != null ? (Map) importer.get(CHECKLISTS) : null);
		if (checklists == null && importer != null && createIfNecessary) {
			importer.put(CHECKLISTS, checklists = new HashMap<Integer,JsonNode>(4));
		}

		return checklists;
	}

	/**
	 * Get the cached checklist for the specified field of the item to import
	 * @param tracker is the tracker sync configuration
	 * @param item is the current tracker item to import
	 * @param field is the checklist field
	 * @param importer is the current import cache/support
	 * @param statistic are the current import statistics
	 * @return cached checklist for the specified field of the item to import
	 */
	public static JsonNode getChecklist(JiraTrackerSyncConfig tracker, TrackerItemDto item, TrackerLayoutLabelDto field, ImporterSupport importer, ImportStatistics statistic) {
		JsonNode checklist = null;

		if (item != null && field != null && importer != null) {
			Map<Integer,JsonNode> checklists = getChecklists(importer, true);

			if ((checklist = checklists.get(field.getId())) == null) {
				TrackerItemDto orig = getOriginalItem(item, statistic);

				if ((checklist = getChecklist(tracker, orig, field)) == null) {
					checklist = jsonMapper.createArrayNode();
				}

				checklists.put(field.getId(), checklist);
			}
		}

		return checklist;
	}

	/**
	 * Set the cached checklist for the specified field of the item to import
	 * @param item is the current tracker item to import
	 * @param field is the checklist field
	 * @param importer is the current import cache/support
	 * @param checklist is the new checklist to cache for the specified item field
	 * @return the old checklist value (if any), that was cached for the specified item field
	 */
	public static JsonNode setChecklist(TrackerItemDto item, TrackerLayoutLabelDto field, JsonNode checklist, ImporterSupport importer) {
		JsonNode result = null;

		if (item != null && field != null && importer != null) {
			Map<Integer,JsonNode> checklists = getChecklists(importer, checklist != null);

			if (checklist != null) {
				result = checklists.put(field.getId(), checklist);
			} else if (checklists != null) {
				result = checklists.remove(field.getId());
			}
		}
		return result;
	}

	public static Map<Integer,Change> getChanges(Object value) {
		return value instanceof Map ? (Map)value : null;
	}

	/**
	 * Convert the specified incremental checklist change into an appropriate Checklist Wikitext change
	 * @param tracker is the tracker sync configuration
	 * @param item is the tracker item to import
	 * @param fieldChange is the incremental custom checklist field change
	 * @param importer is the current import cache/support
	 * @param statistic are the current import statistics
	 */
	@CustomField.ImportFieldChange
	public void buildTrackerItemHistoryConfiguration(JiraTrackerSyncConfig tracker, TrackerItemDto item, TrackerItemHistoryConfiguration fieldChange, ImporterSupport importer, ImportStatistics statistic) {
		TrackerLayoutLabelDto field;
		Map<Integer,Change>	  newValues;

		if (fieldChange != null && (field = fieldChange.getField()) != null
								&& (newValues = getChanges(fieldChange.getNewValueObject())) != null && newValues.size() > 0) {
			Map<Integer,Change> oldValues = getChanges(fieldChange.getOldValueObject());

			fieldChange.setOldValueObject(null);
			fieldChange.setNewValueObject(null);

			JsonNode  oldItems = getChecklist(tracker, item, field, importer, statistic);
			Checklist modified = new Checklist(oldItems.deepCopy());

			for (Map.Entry<Integer,Change> change : newValues.entrySet()) {
				Change newItem = change.getValue();
				Change oldItem = oldValues.get(change.getKey());

				if (newItem.wasAdded()) {
					newItem.apply(tracker, modified.addItem());
				} else if (newItem.wasRemoved()) {
					modified.removeItem(StringUtils.defaultString(oldItem != null ? oldItem.getName() : null,  newItem.getName()));
				} else if (newItem.wasReordered()) {
					modified.reorderItems();
				} else {
					// We must NOT add the item here, if no such item exists (any more), because it was most probably removed locally and MUST remain removed !
					ObjectNode node = modified.getItem(StringUtils.defaultString(newItem.wasRenamed() && oldItem != null ? oldItem.getName() : null,  newItem.getName()));
					if (node != null) {
						if (!newItem.isHeader() && oldItem != null && oldItem.isHeader()) {
							node.remove(HEADER);
						}

						newItem.apply(tracker, node);
					}
				}
			}

			modified.applyOrder(getChecklist(tracker, item, field));

			fieldChange.setOldValue(wrapChecklist(oldItems));
			fieldChange.setNewValue(wrapChecklist(modified.getItems()));

			setChecklist(item, field, modified.getItems(), importer);
		}
	}

	/**
	 * Remove the cached checklist for the specified field of the specified item, after the item import is finished
	 * @param item is the current tracker item to import
	 * @param field is the checklist field
	 * @param importer is the current import cache/support
	 */
	@CustomField.ImportFinished
	public void resetChecklist(TrackerItemDto item, TrackerLayoutLabelDto field, ImporterSupport importer) {
		Map<Integer,JsonNode> checklists = getChecklists(importer, false);
		if (checklists != null) {
			if (field != null) {
				checklists.remove(field.getId());
			} else {
				checklists.clear();
			}
		}
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

}
