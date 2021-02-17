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

import static com.intland.codebeamer.controller.AbstractJsonController.jsonMapper;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.BODY;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PLUGIN_FOOTER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PLUGIN_HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.prepareChecklist;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.unwrapChecklist;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.wrapChecklist;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.persistence.dto.UserDto;
import com.intland.codebeamer.servlet.CBPaths;
import com.intland.codebeamer.wiki.CodeBeamerWikiContext;

/**
 * TestNG tests for the {@link ChecklistPlugin}
 * 
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Test(groups = { "plugins" })
public class ChecklistPluginNGTests {

	public static ObjectNode createChecklistItem(Integer id, String name, boolean checked) {
		assertNotNull(name, "Checklist item name required");

		ObjectNode item = jsonMapper.createObjectNode();

		if (id != null) {
			item.set(ID, IntNode.valueOf(id.intValue()));
		}

		item.set(NAME, TextNode.valueOf(name));

		if (checked) {
			item.set(CHECKED, BooleanNode.TRUE);
		}

		return item;
	}

	@Test
	public void testWrapUnwrapAndPrepareChecklist() throws Exception {
		ArrayNode checklist = jsonMapper.createArrayNode();
		ObjectNode item = createChecklistItem(null, "Do something\n>>\nExample checklist item", false);

		checklist.add(item);

		String checklistMarkup = wrapChecklist(checklist);
		assertNotNull(checklistMarkup, "Checklist wiki markup");
		assertTrue(checklistMarkup.startsWith(PLUGIN_HEADER),
				"Checklist markup wrapped into Checklist wiki plugin envelope");
		assertTrue(checklistMarkup.endsWith(PLUGIN_FOOTER),
				"Checklist markup wrapped into Checklist wiki plugin envelope");

		JsonNode unwrapped = unwrapChecklist(checklistMarkup);
		assertNotNull(unwrapped, "Unwrapped checklist");
		assertTrue(unwrapped.isArray(), "Unwrapped checklist is array of checklist items");
		assertEquals(unwrapped.size(), checklist.size(), "Unwrapped Checklist size");

		JsonNode item_ = unwrapped.get(0);
		assertNotNull(item_, "Unwapped checklist item");
		assertTrue(item_.isObject(), "Unwrapped item is object");
		assertEquals(item_, item, "Unwapped checklist item");

		List<Map<String, Object>> prepared = prepareChecklist(unwrapped);
		assertNotNull(prepared, "Prepared checklist");
		assertEquals(prepared.size(), unwrapped.size(), "Prepared checklist size");
	}

	@Test(dependsOnMethods = "testWrapUnwrapAndPrepareChecklist")
	public void testChecklistHtmlRendering() throws Exception {
		CBPaths.getInstance().setCbInstallDir(".");
		CBPaths.getInstance().setCbWebappDir(new File("src/main/web"));
		CodeBeamerWikiContext context = mock(CodeBeamerWikiContext.class);
		UserDto user = mock(UserDto.class);
		when(context.getUser()).thenReturn(user);
		

		ArrayNode checklist = jsonMapper.createArrayNode();
		ObjectNode checkedItem = createChecklistItem(Integer.valueOf(1), "Checked Item", true);
		ObjectNode uncheckedItem = createChecklistItem(Integer.valueOf(2), "Unchecked Item", false);

		checklist.add(checkedItem);
		checklist.add(uncheckedItem);

		ChecklistPlugin plugin = new ChecklistPlugin();

		String html = plugin.execute(context, Collections.singletonMap("_body", BODY.toPrettyJSONString(checklist)));
		assertNotNull(html, "Rendered Checklist HTML");

		Document body = Jsoup.parseBodyFragment(html);
		assertNotNull(body, "Parsed HTML document");

		Element stylesheet = body.select("link[rel=stylesheet][href$=ChecklistPlugin.css]").first();
		assertNotNull(stylesheet, "HTML includes ChecklistPlugin.css");

		Element table = body.select("table.checklist").first();
		assertNotNull(table, "HTML includes <table class=\"checkist\">");

		Elements items = table.select("tr.checklistItem");
		assertEquals(items.size(), checklist.size(), "Number of <tr class=\"checklistItem\">");

		assertChecked(true, items.get(0));
		assertChecked(true, items.get(1));
	}


	private void assertChecked(boolean checked, Element item) {
		Element checkbox = item.select("input[type=checkbox]").first();
		assertEquals(checked, checkbox.attr("checked") != null);
	}

}
