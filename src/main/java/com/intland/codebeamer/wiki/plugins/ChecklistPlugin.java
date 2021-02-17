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
package com.intland.codebeamer.wiki.plugins;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;

import com.ecyrd.jspwiki.WikiContext;
import com.ecyrd.jspwiki.plugin.PluginException;

import com.fasterxml.jackson.databind.JsonNode;

import com.intland.codebeamer.controller.AbstractJsonController;
import com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto;
import com.intland.codebeamer.persistence.dto.AttributedDto;
import com.intland.codebeamer.persistence.dto.TrackerChoiceOptionDto;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.UserDto;
import com.intland.codebeamer.persistence.dto.base.DescribeableDto;
import com.intland.codebeamer.persistence.dto.base.NamedDto;
import com.intland.codebeamer.persistence.util.PersistenceUtils;
import com.intland.codebeamer.utils.AnchoredPeriod.Edge;
import com.intland.codebeamer.wiki.plugins.base.AbstractCodeBeamerWikiPlugin;

import static com.intland.codebeamer.controller.support.ResponseViewHandler.ISO_DATE_TIME;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.STYLE;
import static com.intland.codebeamer.utils.AnchoredPeriod.TODAY;


/**
 * A Wiki Plugin to render a Checklist, that is defined as a JSON array of checklist items, in the plugin body into an appropriate HTML table.<br/>
 *
 * <p>Each checklist item is a JSON view of a {@link TrackerItemDto}, where currently only the following attributes are used <ul>
 *   <li>{@link TrackerSyncConfigurationDto#ID}</li>
 *   <li>{@link TrackerSyncConfigurationDto#NAME}</li>
 *   <li>{@link TrackerSyncConfigurationDto#DESCRIPTION}</li>
 *   <li>{@link #PRIORITY}</li>
 *   <li>{@link #STATUS}</li>
 *   <li>{@link #END_DATE}</li>
 * </ul></p>
 *
 * <p>Each checklist item also has additional checklist specific boolean attributes (a missing attribute means false) <ul>
 *   <li>{@link #PINNED} - whether this item should be pinned, so that it cannot be removed or re-positioned </li>
 *   <li>{@link #HEADER} - whether this item is a header item</li>
 *   <li>{@link #MANDATORY} - whether this item is a mandatory item or not</li>
 *   <li>{@link #CHECKED} - whether this item is checked or not</li>
 * </ul></p>
 *
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
public class ChecklistPlugin extends AbstractCodeBeamerWikiPlugin {
	public static final Logger logger = Logger.getLogger(ChecklistPlugin.class);

	public static final String PLUGIN_HEADER = "[{Checklist\r\n\r\n";
	public static final String PLUGIN_FOOTER = "\r\n}]";

	public static final String PINNED 		= "pinned";
	public static final String HEADER 		= "header";
	public static final String MANDATORY 	= "mandatory";
	public static final String CHECKED 	 	= "checked";
	public static final String PRIORITY		= "priority";
	public static final String STATUS  	 	= "status";
	public static final String END_DATE 	= "endDate";

	public static final List<String> PRIORITIES = Collections.unmodifiableList(Arrays.asList("None", "Highest", "High", "Normal", "Low", "Lowest"));

	public static final Map<String,String> STATUS_NAME = new TreeMap<String,String>();
	static {
		STATUS_NAME.put("notApplicable", "N/A");
		STATUS_NAME.put("inProgress", 	 "In Progress");
		STATUS_NAME.put("blocked", 		 "Blocked");
	}

	/**
	 * A helper to encode/decode the Checklist body to/from JSON
	 */
	public static final AbstractJsonController BODY = new AbstractJsonController() {
		@Override
		protected Logger getLogger() {
			return logger;
		}
	};

	/**
	 * Wrap the specified checklist body into WIKI markup for this plugin
	 * @param checklist should be a JSON array of checklist items to wrap into WIKI plugin markup
	 * @return the Wiki markup for the checklist
	 */
	public static String wrapChecklist(JsonNode checklist) {
		StringBuilder markup = new StringBuilder(1024).append(PLUGIN_HEADER);

		if (checklist != null && checklist.isArray()) {
			markup.append(BODY.toPrettyJSONString(checklist));
		}

		return markup.append(PLUGIN_FOOTER).toString();
	}

	/**
	 * Unwrap the checklist, that is stored in the specified Wiki markup
	 * @param markup should be WIKI markup for this plugin
	 * @return the JSON array of checklist items as stored in the Wiki markup
	 */
	public static JsonNode unwrapChecklist(String markup) {
		return BODY.parseJSON(StringUtils.substringBetween(markup, PLUGIN_HEADER, PLUGIN_FOOTER));
	}

	/**
	 * Encode a {@link Date}, e.g. {@link #END_DATE}, into a checklist item ISO date/time string value
	 * @param date to encode into an ISO date/time string
	 * @return the ISO date/time string, or null, if the date was null
	 */
	public static String encodeIsoDate(Date date) {
		return date != null ? ISO_DATE_TIME.print(date.getTime()) : null;
	}

	/**
	 * Decode an ISO date/time string, e.g. {@link #END_DATE}, into a {@link Date}
	 * @param dateStr should be an ISO date/time string, or null
	 * @return the decoded {@link Date}, or null, if the dateStr was blank
	 * @throws IllegalArgumentException if the dateStr is not an ISO date/time string
	 */
	public static Date decodeIsoDate(String dateStr) throws IllegalArgumentException {
		return StringUtils.isNotBlank(dateStr) ? new Date(ISO_DATE_TIME.parseMillis(dateStr)) : null;
	}

	/**
	 * Convert the specified priority, that can either be a priority object, a priority id or a priority name into a named priority
	 * @param priority to convert into a named priority
	 * @return the priority as as {@link NamedDto}, or null
	 */
	public static NamedDto getPriority(Object priority) {
		String name = StringUtils.trimToNull(PersistenceUtils.getName(priority));
		if (StringUtils.isNumeric(name)) {
			name = null;
		}

		Integer id = PersistenceUtils.getId(priority);
		if (id == null && name != null) {
			id = Integer.valueOf(PRIORITIES.indexOf(StringUtils.capitalize(name.toLowerCase())));
		}

		if (id != null && id.intValue() > 0) {
			int idx = Math.min(PRIORITIES.size() - 1, id.intValue());

			return new NamedDto(Integer.valueOf(idx), StringUtils.defaultIfBlank(name, PRIORITIES.get(idx)));
		}

		return null;
	}

	private static boolean setStatus(TrackerChoiceOptionDto status, String idOrName) {
		if (status != null && (idOrName = StringUtils.trimToNull(idOrName)) != null) {
			for (Map.Entry<String,String> entry : STATUS_NAME.entrySet()) {
				if (StringUtils.equalsIgnoreCase(idOrName, entry.getKey()) ||
					StringUtils.equalsIgnoreCase(idOrName, entry.getValue())) {
					status.setStyle(entry.getKey());
					status.setName(entry.getValue());

					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Convert the specified status, that can either be a status object, a status id or a status name into a status option
	 * @param status to convert into a status option
	 * @return the status as as {@link TrackerChoiceOptionDto}, or null
	 */
	public static TrackerChoiceOptionDto getStatus(Object status) {
		TrackerChoiceOptionDto result = new TrackerChoiceOptionDto(PersistenceUtils.getId(status), PersistenceUtils.getName(status));

		if (status != null) {
			if (status instanceof Number) {
				result.setName(null);
			} else if (status instanceof Map) {
				Map<?,?> map = (Map<?,?>) status;

				result.setDescription(AttributedDto.toString(map.get(DESCRIPTION)));
				result.setStyle(AttributedDto.toString(map.get(STYLE)));

			} else if (status instanceof JsonNode) {
				JsonNode node = (JsonNode) status;

				if (node.isObject()) {
					result.setDescription(AbstractJsonController.getString(node, DESCRIPTION));
					result.setStyle(AbstractJsonController.getString(node, STYLE));
				} else if (!node.isTextual()) {
					result.setName(null);
				}
			} else if (status instanceof TrackerChoiceOptionDto) {
				TrackerChoiceOptionDto other = (TrackerChoiceOptionDto)status;

				result.setDescription(other.getDescription());
				result.setStyle(other.getStyle());

			} else if (status instanceof DescribeableDto) {
				result.setDescription(((DescribeableDto)status).getDescription());
			}
		}

		if (StringUtils.isBlank(result.getName())) {
			result = null;
		} else if (StringUtils.isBlank(result.getStyle())) {
			setStatus(result, result.getName());
		}

		return result;
	}

	/**
	 * Check and prepare the specified checklist item for rendering
	 * @return true if the item is good for rendering, otherwise false
	 */
	public static boolean prepareChecklistItem(Map<String,Object> item) {
		if (item != null) {
			String name = AttributedDto.toString(item.get(NAME));
			if (StringUtils.isNotBlank(name)) {
				Object priority = item.get(PRIORITY);
				if (priority != null) {
					item.put(PRIORITY, getPriority(priority));
				}

				Object status = item.get(STATUS);
				if (status != null) {
					item.put(STATUS, getStatus(status));
				}

				String endDate = AttributedDto.toString(item.remove(END_DATE));
				if (endDate != null) {
					try {
						item.put(END_DATE, decodeIsoDate(endDate));
					} catch(Throwable ex) {
						logger.warn("Invalid endDate: " + endDate, ex);
					}
				}

				return true;
			}
		}

		return false;
	}

	/**
	 * Check and prepare the specified checklist items for rendering
	 * @param items is a list of checklist items to prepare
	 * @return a List of prepared checklist items
	 */
	public static List<Map<String,Object>> prepareChecklistItems(List<Map<String,Object>> items) {
		if (items != null && items.size() > 0) {
			for (Iterator<Map<String,Object>> it = items.iterator(); it.hasNext();) {
				if (!prepareChecklistItem(it.next())) {
					it.remove();
				}
			}
		}

		return items;
	}

	/**
	 * Check and prepare the specified checklist for rendering
	 * @param checklist should be a JSON array of checklist items to prepare for rendering
	 * @return the prepared checklist items as a List
	 */
	public static List<Map<String,Object>> prepareChecklist(JsonNode checklist) {
		if (checklist != null && checklist.isArray()) {
			return prepareChecklistItems(BODY.castJSON(checklist, List.class));
		}

		return Collections.emptyList();
	}

	/**
	 * Render the plugin body, that is a JSON array of Checklist items, into a HTML table
	 * @param context is the plugin context
	 * @param params are the plugin parameters
	 */
	@Override
	public String execute(WikiContext context, Map params) throws PluginException {
		UserDto user = getUserFromContext(context);
		String  body = (String) params.get("_body");

		VelocityContext velocityContext = getDefaultVelocityContextFromContext(context);
		velocityContext.put("checklist", prepareChecklist(BODY.parseJSON(body)));
		velocityContext.put("today", 	 new Date());
		velocityContext.put("tomorrow",  TODAY.getEdge(Edge.End, null, user.getTimeZone()));

		return renderPluginTemplate("ChecklistPlugin.vm", velocityContext);
	}


}
