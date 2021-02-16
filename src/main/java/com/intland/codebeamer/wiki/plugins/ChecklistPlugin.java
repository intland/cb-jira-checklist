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

import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.STYLE;
import static com.intland.codebeamer.utils.AnchoredPeriod.TODAY;

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

	public static final String HEADER 		= "header";
	public static final String MANDATORY 	= "mandatory";
	public static final String CHECKED 	 	= "checked";

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
	 * Check and prepare the specified checklist item for rendering
	 * @return true if the item is good for rendering, otherwise false
	 */
	public static boolean prepareChecklistItem(Map<String,Object> item) {
		if (item != null) {
			String name = AttributedDto.toString(item.get(NAME));
			if (StringUtils.isNotBlank(name)) {
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
		return renderPluginTemplate("ChecklistPlugin.vm", velocityContext);
	}


}
