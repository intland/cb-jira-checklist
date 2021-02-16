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
import static com.intland.codebeamer.controller.AbstractJsonController.getInteger;
import static com.intland.codebeamer.controller.AbstractJsonController.getString;
import static com.intland.codebeamer.controller.AbstractJsonController.jsonMapper;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.DESC_SEP;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.GLOBAL_ID;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.IS_HEADER;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.OPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.persistence.util.TrackerItemFieldHandler.PRIORITY_LABEL_ID;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.MANDATORY;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PINNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.controller.jira.JiraImportController;
import com.intland.codebeamer.controller.jira.JiraTrackerSyncConfig;
import com.intland.codebeamer.extensions.jira.ChecklistForJiraField.Checklist;
import com.intland.codebeamer.manager.util.ImportStatistics;
import com.intland.codebeamer.manager.util.ImporterSupport;
import com.intland.codebeamer.manager.util.TrackerItemHistoryConfiguration;
import com.intland.codebeamer.persistence.dto.TrackerChoiceOptionDto;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
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


	@BeforeClass
	public void setupJiraTrackerSyncConfig() {
		TrackerChoiceOptionDto low    = new TrackerChoiceOptionDto(Integer.valueOf(4), "Low");
		TrackerChoiceOptionDto normal = new TrackerChoiceOptionDto(Integer.valueOf(3), "Normal");
		TrackerChoiceOptionDto high   = new TrackerChoiceOptionDto(Integer.valueOf(2), "High");

		TrackerChoiceOptionDto jiraLow    = new TrackerChoiceOptionDto(Integer.valueOf(3), "Low");
		TrackerChoiceOptionDto jiraMedium = new TrackerChoiceOptionDto(Integer.valueOf(2), "Medium");
		TrackerChoiceOptionDto jiraHigh   = new TrackerChoiceOptionDto(Integer.valueOf(1), "High");

		Map<Object,TrackerChoiceOptionDto> jira2cb = new HashMap<Object,TrackerChoiceOptionDto>(8);

		jira2cb.put(jiraLow.getId(), 	  low);
		jira2cb.put(jiraLow.getName(),    low);
		jira2cb.put(jiraMedium.getId(),   normal);
		jira2cb.put(jiraMedium.getName(), normal);
		jira2cb.put(jiraHigh.getId(),     high);
		jira2cb.put(jiraHigh.getName(),   high);

		tracker.setChoiceValueMapping(PRIORITY_LABEL_ID, jira2cb);

		Map<Integer,TrackerChoiceOptionDto> cb2jira= new HashMap<Integer,TrackerChoiceOptionDto>(4);
		cb2jira.put(low.getId(), 	jiraLow);
		cb2jira.put(normal.getId(), jiraMedium);
		cb2jira.put(high.getId(),   jiraHigh);

		tracker.setOptionExport(PRIORITY_LABEL_ID, cb2jira);
	}

	public static ObjectNode createChecklistItem(Integer id, String name, String description, boolean option, boolean header, boolean mandatory,
													boolean checked) {
		assertNotNull(name, "Checklist item name required");

		ObjectNode item = jsonMapper.createObjectNode();

		if (id != null) {
			item.set(ID, IntNode.valueOf(id.intValue()));

			if (option) {
				item.set(GLOBAL_ID, IntNode.valueOf(id.intValue()));
			}
		}

		if (description == null) {
			item.set(NAME, TextNode.valueOf(name));
		} else {
			item.set(NAME, TextNode.valueOf(name + DESC_SEP + "\n" + description));
		}

		if (header) {
			item.set(IS_HEADER, BooleanNode.TRUE);
		}

		if (mandatory) {
			item.set(MANDATORY, BooleanNode.TRUE);
		}

		if (checked) {
			item.set(CHECKED, BooleanNode.TRUE);
		}

		return item;
	}


	@Test
	public void testJira2cb() throws Exception {
		ArrayNode  checklist = jsonMapper.createArrayNode();
		ObjectNode item      = createChecklistItem(Integer.valueOf(1), "Do *something*", "An **Example** checklist item", true, false, true, false);

		checklist.add(item);

		JsonNode cbChecklist = adapter.jira2cb(tracker, checklist, controller);

		assertNotNull(cbChecklist, "Converted CB checklist");
		assertEquals(cbChecklist.size(), 1, "Number of converted CB checklist items");

		for (JsonNode cbItem : cbChecklist) {
			assertTrue(cbItem != null && cbItem.isObject(), "Converted CB checklist item");

			assertEquals(getString(cbItem, NAME), "Do ''something''", "Converted CB checklist name");
			assertEquals(getString(cbItem, DESCRIPTION), "An __Example__ checklist item", "Converted CB checklist description");

			assertTrue (getBoolean(cbItem, PINNED),    "Converted CB checklist item pinned");
			assertFalse(getBoolean(cbItem, HEADER),    "Converted CB checklist item header");
			assertTrue (getBoolean(cbItem, MANDATORY), "Converted CB checklist item mandatory");
			assertFalse(getBoolean(cbItem, CHECKED),   "Converted CB checklist item checked");
		}
	}

	@Test
	public void testCb2jira() throws Exception {
		ArrayNode  cbChecklist = jsonMapper.createArrayNode();
		ObjectNode cbItem      = ChecklistPluginNGTests.createChecklistItem(Integer.valueOf(123), "Say ''Hallo''", "Otherwise you are a total __Jerk__!",  true, true, true, false);

		cbChecklist.add(cbItem);

		JsonNode checklist = adapter.cb2jira(tracker, cbChecklist);

		assertNotNull(checklist, "Converted Jira checklist");
		assertEquals(checklist.size(), 1, "Number of converted checklist items");

		for (JsonNode item : checklist) {
			assertTrue(item != null && item.isObject(), "Converted Jira checklist item");

			assertEquals(getString(item, NAME), "Say *Hallo*" + DESC_SEP + "\nOtherwise you are a total **Jerk**!", "Converted Jira checklist name");


			assertTrue (getBoolean(item, OPTION),    "Converted Jira checklist item option");
			assertTrue (getBoolean(item, IS_HEADER), "Converted Jira checklist item header");
			assertTrue (getBoolean(item, MANDATORY), "Converted Jira checklist item mandatory");
			assertFalse(getBoolean(item, CHECKED),   "Converted Jira checklist item checked");
		}
	}

	@Test(dependsOnMethods = {"testJira2cb", "testCb2jira"})
	public void testImportExportChecklist() throws Exception {
		ArrayNode  checklist = jsonMapper.createArrayNode();
		ObjectNode item_     = createChecklistItem(Integer.valueOf(7), "Do *something*", "An **Example** checklist item", true, true, true, true);

		checklist.add(item_);

		String imported = adapter.importChecklist(tracker, checklist, controller);
		assertNotNull(imported, "Imported JIRA checklist Wiki markup");

		JsonNode exported = adapter.exportChecklist(tracker, imported);

		assertNotNull(exported, "Exported Jira checklist");
		assertEquals(exported.size(), 1, "Number of exported checklist items");

		for (JsonNode item : exported) {
			assertTrue(item != null && item.isObject(), "Exported Jira checklist item");

			assertEquals(getString(item, NAME), "Do *something*" + DESC_SEP + "\nAn **Example** checklist item", "Exported Jira checklist name");
			assertEquals (getInteger(item, GLOBAL_ID), Integer.valueOf(7),   "Exporteded Jira checklist item option");

			assertTrue(getBoolean(item, IS_HEADER), "Exported Jira checklist item header");
			assertTrue(getBoolean(item, MANDATORY), "Exported Jira checklist item mandatory");
			assertTrue(getBoolean(item, CHECKED),   "Exported Jira checklist item checked");
		}
	}

	public static Checklist createChecklist() {
		Checklist  checklist = new Checklist(null);
		ObjectNode item      = ChecklistPluginNGTests.createChecklistItem(Integer.valueOf(123), "Deploy to ''staging'' server", "Required __before__ deploying to production!", true, false, true, true);

		checklist.getItems().add(item);

		return checklist;
	}


}
