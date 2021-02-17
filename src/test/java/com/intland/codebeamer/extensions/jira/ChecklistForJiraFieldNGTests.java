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

import static com.intland.codebeamer.controller.AbstractJsonController.getBoolean;
import static com.intland.codebeamer.controller.AbstractJsonController.getString;
import static com.intland.codebeamer.controller.AbstractJsonController.jsonMapper;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
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


}
