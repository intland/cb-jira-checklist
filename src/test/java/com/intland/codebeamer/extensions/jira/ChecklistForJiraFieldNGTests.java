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
package com.intland.codebeamer.extensions.jira;

import static com.intland.codebeamer.controller.AbstractJsonController.getBoolean;
import static com.intland.codebeamer.controller.AbstractJsonController.getString;
import static com.intland.codebeamer.controller.AbstractJsonController.jsonMapper;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.controller.jira.JiraImportController;
import com.intland.codebeamer.controller.jira.JiraTrackerSyncConfig;
import com.intland.codebeamer.manager.util.ImportStatistics;
import com.intland.codebeamer.manager.util.ImporterSupport;
import com.intland.codebeamer.manager.util.TrackerItemHistoryConfiguration;
import com.intland.codebeamer.manager.util.TrackerItemNumbers;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.TrackerItemRevisionDto;
import com.intland.codebeamer.persistence.dto.TrackerLayoutLabelDto;
import com.intland.codebeamer.wiki.plugins.ChecklistPluginNGTests;

import net.sf.mpxj.CustomField;



/**
 * Tests for the {@link ChecklistForJiraField} {@link CustomField} adapter
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Test(groups = {"controller"})
public class ChecklistForJiraFieldNGTests {
	@Autowired
	private JiraImportController controller;

	private ChecklistForJiraField adapter = new ChecklistForJiraField();
	private JiraTrackerSyncConfig tracker = new JiraTrackerSyncConfig();

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
	public void testJira2cb() throws Exception {
		ArrayNode  checklist = jsonMapper.createArrayNode();
		ObjectNode item      = createChecklistItem(Integer.valueOf(1), "Do *something*\n>>\nAn **Example** checklist item", false);

		checklist.add(item);

		JsonNode cbChecklist = adapter.jira2cb(tracker, checklist, controller);

		assertNotNull(cbChecklist, "Converted CB checklist");
		assertEquals(cbChecklist.size(), 1, "Number of converted CB checklist items");

		for (JsonNode cbItem : cbChecklist) {
			assertTrue(cbItem != null && cbItem.isObject(), "Converted CB checklist item");
			assertEquals(getString(cbItem, NAME), "Do ''something''\n>>\nAn __Example__ checklist item", "Converted CB checklist name\n>>");
			assertFalse(getBoolean(cbItem, CHECKED),   "Converted CB checklist item checked");
		}
	}

	@Test
	public void testCb2jira() throws Exception {
		ArrayNode  cbChecklist = jsonMapper.createArrayNode();
		ObjectNode cbItem      = ChecklistPluginNGTests.createChecklistItem(Integer.valueOf(123), "Say ''Hallo''\n>>\nOtherwise you are a total __Jerk__!",  false);

		cbChecklist.add(cbItem);

		JsonNode checklist = adapter.cb2jira(tracker, cbChecklist);

		assertNotNull(checklist, "Converted Jira checklist");
		assertEquals(checklist.size(), 1, "Number of converted checklist items");

		for (JsonNode item : checklist) {
			assertTrue(item != null && item.isObject(), "Converted Jira checklist item");

			assertEquals(getString(item, NAME), "Say *Hallo*\n>>\nOtherwise you are a total **Jerk**!", "Converted Jira checklist name");
			assertFalse(getBoolean(item, CHECKED),   "Converted Jira checklist item checked");
		}
	}

	@Test(dependsOnMethods = {"testJira2cb", "testCb2jira"})
	public void testImportExportChecklist() throws Exception {
		ArrayNode  checklist = jsonMapper.createArrayNode();
		ObjectNode item_     = createChecklistItem(Integer.valueOf(7), "Do *something*\n>>\nAn **Example** checklist item", true);

		checklist.add(item_);

		String imported = adapter.importChecklist(tracker, checklist, controller);
		assertNotNull(imported, "Imported JIRA checklist Wiki markup");

		JsonNode exported = adapter.exportChecklist(tracker, imported);

		assertNotNull(exported, "Exported Jira checklist");
		assertEquals(exported.size(), 1, "Number of exported checklist items");

		for (JsonNode item : exported) {
			assertTrue(item != null && item.isObject(), "Exported Jira checklist item");

			assertEquals(getString(item, NAME), "Do *something*\n>>\nAn **Example** checklist item", "Exported Jira checklist name");
			assertTrue(getBoolean(item, CHECKED),   "Exported Jira checklist item checked");
		}
	}
	
	@Test
	public void testBuildHistory() {
		TrackerLayoutLabelDto field1 = new TrackerLayoutLabelDto(10000, "DoD", TrackerLayoutLabelDto.WIKITEXT);
		TrackerLayoutLabelDto field2 = new TrackerLayoutLabelDto(10001, "Acceptance Criteria", TrackerLayoutLabelDto.WIKITEXT);
		
		TrackerItemDto item1old = new TrackerItemDto(1);
		item1old.setCustomField(0, "old");
		item1old.setCustomField(1, "old");
		
		TrackerItemDto item2old = new TrackerItemDto(2);
		item2old.setCustomField(0, "old");
		item2old.setCustomField(1, "old");
		
		TrackerItemDto item1 = item1old.clone();
		item1.setCustomField(0, "current");
		item1.setCustomField(1, "current");
		
		TrackerItemDto item2 = item2old.clone();
		item2.setCustomField(0, "current");
		item2.setCustomField(1, "current");
		
		TrackerItemNumbers numbers = new TrackerItemNumbers(true);
		numbers.addUpdated(item1old, item1);
		numbers.addUpdated(item2old, item2);
		ImportStatistics statistics = new ImportStatistics();
		statistics.put(JiraTrackerSyncConfig.ISSUES, numbers);

		
		ImporterSupport support = new ImporterSupport();
		
		TrackerItemHistoryConfiguration item1Field1Change1 = buildHistory(item1, field1, support, statistics);
		assertEquals(item1Field1Change1.getField(), field1);
		assertEquals(item1Field1Change1.getOldValue(), "old");
		assertEquals(item1Field1Change1.getNewValue(), "current");
		
		TrackerItemHistoryConfiguration item2Field1Change1 = buildHistory(item2, field1, support, statistics);
		assertEquals(item2Field1Change1.getField(), field1);
		assertEquals(item2Field1Change1.getOldValue(), "old");
		assertEquals(item2Field1Change1.getNewValue(), "current");
		
		TrackerItemHistoryConfiguration item1Field2Change1 = buildHistory(item1, field2, support, statistics);
		assertEquals(item1Field2Change1.getField(), field2);
		assertEquals(item1Field2Change1.getOldValue(), "old");
		assertEquals(item1Field2Change1.getNewValue(), "current");
		
		TrackerItemHistoryConfiguration item1Field1Change2 = buildHistory(item1, field1, support, statistics);
		assertNull(item1Field1Change2.getField());
	}


	private TrackerItemHistoryConfiguration buildHistory(TrackerItemDto item, TrackerLayoutLabelDto field, ImporterSupport support, ImportStatistics statistics) {
		TrackerItemHistoryConfiguration fieldChange = new TrackerItemHistoryConfiguration(new TrackerItemRevisionDto(), field, "1", "2");
		adapter.buildHistory(item, fieldChange, support, statistics);
		return fieldChange;
	}


}
