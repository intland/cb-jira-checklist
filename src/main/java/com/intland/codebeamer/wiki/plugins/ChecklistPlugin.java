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

import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;

import com.ecyrd.jspwiki.WikiContext;
import com.ecyrd.jspwiki.plugin.PluginException;
import com.fasterxml.jackson.databind.JsonNode;
import com.intland.codebeamer.controller.AbstractJsonController;
import com.intland.codebeamer.wiki.plugins.base.AbstractCodeBeamerWikiPlugin;


/**
 * A Wiki Plugin to render a Checklist, that is defined as a JSON array of checklist items, in the plugin body into an appropriate HTML table.<br/>
 *
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
public class ChecklistPlugin extends AbstractCodeBeamerWikiPlugin {
	public static final Logger logger = Logger.getLogger(ChecklistPlugin.class);

	public static final String PLUGIN_HEADER = "[{Checklist\r\n\r\n";
	public static final String PLUGIN_FOOTER = "\r\n}]";

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
	 * Check and prepare the specified checklist items for rendering
	 * @param items is a list of checklist items to prepare
	 * @return a List of prepared checklist items
	 */
	public static List<Map<String,Object>> prepareChecklistItems(List<Map<String,Object>> items) {
		if (items != null && items.size() > 0) {
			for (Iterator<Map<String,Object>> it = items.iterator(); it.hasNext();) {
				Object name = it.next().get(NAME);
				if (name == null || StringUtils.isEmpty(name.toString())) {
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
		String  body = (String) params.get("_body");

		VelocityContext velocityContext = getDefaultVelocityContextFromContext(context);
		velocityContext.put("checklist", prepareChecklist(BODY.parseJSON(body)));
		return renderPluginTemplate("ChecklistPlugin.vm", velocityContext);
	}


}
