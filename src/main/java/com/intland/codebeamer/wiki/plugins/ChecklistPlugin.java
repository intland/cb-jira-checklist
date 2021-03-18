/**
 * Copyright 2021 Intland Software GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
	@SuppressWarnings("unchecked")
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
	public String execute(WikiContext context, @SuppressWarnings("rawtypes") Map params) throws PluginException {
		String  body = (String) params.get("_body");

		VelocityContext velocityContext = getDefaultVelocityContextFromContext(context);
		velocityContext.put("checklist", prepareChecklist(BODY.parseJSON(body)));
		return renderPluginTemplate("ChecklistPlugin.vm", velocityContext);
	}


}
