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
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.ASSIGNEE_IDS;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.DESC_SEP;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.GLOBAL_ID;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.IS_HEADER;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.OPTION;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.getChanges;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.getChecklist;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.getChecklists;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.getOriginalItem;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraField.setChecklist;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.persistence.util.PersistenceUtils.getToday;
import static com.intland.codebeamer.persistence.util.TrackerItemFieldHandler.PRIORITY_LABEL_ID;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.CHECKED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.MANDATORY;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PINNED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
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
import com.intland.codebeamer.extensions.jira.ChecklistForJiraField.Change;
import com.intland.codebeamer.extensions.jira.ChecklistForJiraField.Checklist;
import com.intland.codebeamer.manager.util.ImportStatistics;
import com.intland.codebeamer.manager.util.ImporterSupport;
import com.intland.codebeamer.manager.util.TrackerItemHistoryConfiguration;
import com.intland.codebeamer.persistence.dto.TrackerChoiceOptionDto;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.TrackerLayoutLabelDto;
import com.intland.codebeamer.wiki.plugins.ChecklistPlugin;
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
		Date	   tomorrow    = new Date(getToday(1).getTime());
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
		Date	   tomorrow  = new Date(getToday(1).getTime());
		Checklist  checklist = new Checklist(null);
		ObjectNode item      = ChecklistPluginNGTests.createChecklistItem(Integer.valueOf(123), "Deploy to ''staging'' server", "Required __before__ deploying to production!", true, false, true, true);

		checklist.getItems().add(item);

		return checklist;
	}

	@Test(dependsOnMethods = {"testJira2cb", "testCb2jira"})
	public void testChecklistItemChanges() throws Exception {
		Checklist checklist = createChecklist();

		String changeString = "1) [Unchecked] (N/A) Deploy to *staging* server\n>>\nRequired **before** deploying to production!";

		Map<Integer,Change> changes = adapter.getItemChanges(changeString, controller);
		assertNotNull(changes, "Parsed checklist item changes");
		assertEquals(changes.size(), 1, "Number of parsed changes");

		Change change = changes.get(Integer.valueOf(1));
		assertNotNull(change, "Change #1");
		assertEquals(change.getName(), "Deploy to ''staging'' server", "Change #1 name");
		assertEquals(change.getDescription(), "Required __before__ deploying to production!", "Change #1 description");
		assertEquals(change.getStatus(), "N/A", "Change #1 status");
		assertTrue(change.hasChanged("unchecked"), "Change #1 unchecked");

		ObjectNode target = checklist.getItem(change.getName());
		assertNotNull(target, "Target item for Change #1");
		assertTrue(getBoolean(target, CHECKED), "Target item checked before change");

		change.apply(tracker, target);
		assertFalse(getBoolean(target, CHECKED), "Target item unchecked after change");

		changeString = "1) [Status changed, Checked] Deploy to *staging* server\n>>\nRequired **before** deploying to production!" + "\n" +
		               "2) [Added, mandatory] (blocked) Build release {05/Feb/2021}(klaus)(High!)";

		changes = adapter.getItemChanges(changeString, controller);
		assertNotNull(changes, "Parsed checklist item changes");
		assertEquals(changes.size(), 2, "Number of parsed changes");

		change = changes.get(Integer.valueOf(1));
		assertNotNull(change, "Change #1");
		assertEquals(change.getName(), "Deploy to ''staging'' server", "Change #1 name");
		assertEquals(change.getDescription(), "Required __before__ deploying to production!", "Change #1 description");
		assertTrue(change.hasChanged("checked"), "Change #1 checked");
		assertTrue(change.hasChanged("status changed"), "Change #1 status change");
		assertNull(change.getStatus(), "Change #1 status");

		target = checklist.getItem(change.getName());
		assertNotNull(target, "Target item for Change #1");
		assertFalse(getBoolean(target, CHECKED), "Target item unchecked before change");

		change.apply(tracker, target);

		assertTrue(getBoolean(target, CHECKED), "Target item checked after change");

		Date dueDate = Change.decodeDueDate("05/Feb/21");
		assertNotNull(dueDate, "Decoded Due Date 05/Feb/21");

		Calendar cal = Calendar.getInstance();
		cal.setTime(dueDate);

		assertEquals(cal.get(Calendar.DAY_OF_MONTH), 5, "Day of month");
		assertEquals(cal.get(Calendar.MONTH), Calendar.FEBRUARY, "Month");
		assertEquals(cal.get(Calendar.YEAR), 2021, "Year");

		change = changes.get(Integer.valueOf(2));
		assertNotNull(change, "Change #2");
		assertTrue(change.wasAdded(), "Change #2 added");
		assertEquals(change.getName(), "Build release", "Change #2 name");
		assertNull(change.getDescription(), "Change #2 description");
		assertTrue(change.hasChanged("status changed"), "Change #2 status change");
		assertEquals(change.getStatus(), "blocked", "Change #2 status");
		assertTrue(change.hasChanged("priority changed"), "Change #2 priority change");
		assertEquals(change.getPriority(), "High", "Change #2 priority");
		assertTrue(change.hasChanged("due date changed"), "Change #2 due date change");
		assertEquals(change.getDueDate(), dueDate, "Change #2 due date");
		assertTrue(change.hasChanged("assigned"), "Change #2 assigned");
		assertEquals(change.getAssigneeIds(), "klaus", "Change #2 assigneeIds");
		assertTrue(change.hasChanged(MANDATORY), "Change #2 mandatory");

		target = checklist.addItem();
		assertNotNull(target, "New checklist item");

		change.apply(tracker, target);

		assertFalse(getBoolean(target, OPTION), "Item is global option");
		assertFalse(getBoolean(target, HEADER), "Item is header");
		assertEquals(getString(target, NAME), change.getName(), "New Item name");
		assertNull(getString(target, DESCRIPTION), "New Item description");
		assertTrue(getBoolean(target, MANDATORY), "New Item is mandatory");
		assertFalse(getBoolean(target, CHECKED), "New Item is checked");

		JsonNode assigneeIds = target.get(ASSIGNEE_IDS);
		assertNotNull(assigneeIds, "New item assigneeIds");
		assertTrue(assigneeIds.isArray(), "New item assigneeIds is array");
		assertEquals(assigneeIds.size(), 1, "Number of new item assigneeIds");
		assertEquals(getString(assigneeIds.get(0), NAME), "klaus", "Name of first assignee");


		changeString = "1) [Modified, Optional, Priority changed, Due date changed] Build release\n>>\nAs self extracting Zip {28/Feb/2021}(Medium!)";

		changes = adapter.getItemChanges(changeString, controller);
		assertNotNull(changes, "Parsed checklist item changes");
		assertEquals(changes.size(), 1, "Number of parsed changes");

		change = changes.get(Integer.valueOf(1));
		assertNotNull(change, "Change #1");
		assertEquals(change.getName(), "Build release", "Change #1 name");
		assertTrue(change.hasChanged("modified"), "Change #1  modified");
		assertEquals(change.getDescription(), "As self extracting Zip", "Change #1 description");
		assertTrue(change.hasChanged("optional"), "Change #1 optional");
		assertTrue(change.hasChanged("priority changed"), "Change #1 priority changed");
		assertEquals(change.getPriority(), "Medium", "Change #1 priority");
		assertTrue(change.hasChanged("due date changed"), "Change #1 due date changed");
		assertNotNull(dueDate = change.getDueDate(), "Change #1 due date");

		target = checklist.getItem(change.getName());
		assertNotNull(target, "Target item for Change #1");

		change.apply(tracker, target);

		assertFalse(getBoolean(target, OPTION), "Item is global option");
		assertFalse(getBoolean(target, HEADER), "Item is header");
		assertEquals(getString(target, NAME), change.getName(), "Updated Item name");
		assertEquals(getString(target, DESCRIPTION), "As self extracting Zip", "Updated Item description");
		assertFalse(getBoolean(target, MANDATORY), "Updated Item mandatory");
		assertFalse(getBoolean(target, CHECKED), "Updated Item is checked");

	}

	@Test(dependsOnMethods = {"testChecklistItemChanges"})
	public void testChecklistCaching() throws Exception {
		ImporterSupport importer = new ImporterSupport();

		Map<Integer,JsonNode> cache = getChecklists(importer, false);
		assertNull(cache, "Checklists cache");

		cache = getChecklists(importer, true);
		assertNotNull(cache, "Checklists cache");

		TrackerItemDto item = new TrackerItemDto(Integer.valueOf(1000));

		TrackerLayoutLabelDto field = new TrackerLayoutLabelDto(TrackerLayoutLabelDto.getCustomFieldId(0), "DoD");
		field.setInputType(TrackerLayoutLabelDto.WIKITEXT);

		assertFalse(cache.containsKey(field.getId()), "Field checklist cached");

		ArrayNode checklist = jsonMapper.createArrayNode();
		checklist.add(ChecklistPluginNGTests.createChecklistItem(null, "!4 Test items", null, true, true, false, false));

		field.setValue(item, ChecklistPlugin.wrapChecklist(checklist));

		JsonNode checklist_ = getChecklist(tracker, item, field);
		assertNotNull(checklist_, "Checklist stored in item field");
		assertNotSame(checklist_, checklist, "Checklist stored in item field");
		assertEquals(checklist_, checklist, "Checklist stored in item field");

		ImportStatistics statistics = new ImportStatistics();
		statistics.items(JiraTrackerSyncConfig.ISSUES, true).addUpdated(item, item);

		TrackerItemDto item_ = getOriginalItem(item, statistics);
		assertSame(item_, item, "Original item");

		checklist_ = getChecklist(tracker, item, field, importer, statistics);
		assertNotNull(checklist_, "Cached field checklist");
		assertEquals(checklist_, checklist, "Cached field checklist");
		assertTrue(cache.containsKey(field.getId()), "Field checklist cached");

		checklist.add(ChecklistPluginNGTests.createChecklistItem(null, "Test item", null, true, false, true, false));

		setChecklist(item, field, checklist, importer);

		checklist_ = getChecklist(tracker, item, field, importer, statistics);
		assertSame(checklist_, checklist, "Cached checklist");

		adapter.resetChecklist(item, field, importer);
		assertFalse(cache.containsKey(field.getId()), "Field checklist cached");
	}

	@Test(dependsOnMethods = {"testChecklistItemChanges", "testChecklistCaching"})
	public void testBuildTrackerItemHistoryConfiguration() throws Exception {
		TrackerItemDto  	  item		 = new TrackerItemDto(Integer.valueOf(1000));
		TrackerLayoutLabelDto field		 = new TrackerLayoutLabelDto(TrackerLayoutLabelDto.getCustomFieldId(0), "DoD");
		ImporterSupport 	  importer 	 = new ImporterSupport();
		ImportStatistics 	  statistics = new ImportStatistics();

		field.setInputType(TrackerLayoutLabelDto.WIKITEXT);
		statistics.items(JiraTrackerSyncConfig.ISSUES, true).addImported(item);

		TrackerItemHistoryConfiguration change1 = new TrackerItemHistoryConfiguration(item, Integer.valueOf(1), field, null, null);
		change1.setOldValueObject(adapter.getItemChanges("", controller));
		change1.setNewValueObject(adapter.getItemChanges("1) [Added][H] Deployment Tasks\n" +
														 "2) [Added, mandatory] (blocked) Build release {05/Feb/2021}(klaus)(High!)", controller));

		Map<Integer,Change> oldChanges = getChanges(change1.getOldValueObject());
		assertNotNull(oldChanges, "Old changes");
		assertTrue(oldChanges.isEmpty(), "Old changes empty");

		Map<Integer,Change> newChanges = getChanges(change1.getNewValueObject());
		assertNotNull(newChanges, "Old changes");
		assertEquals(newChanges.size(), 2, "New changes size");

		adapter.buildTrackerItemHistoryConfiguration(tracker, item, change1, importer, statistics);

		String oldMarkup = change1.getOldValue();
		assertNotNull(oldMarkup, "Wiki Checklist before change");

		JsonNode checklistBefore = ChecklistPlugin.unwrapChecklist(oldMarkup);
		assertNotNull(checklistBefore, "Checklist before change");
		assertTrue(checklistBefore.isArray() && checklistBefore.isEmpty(), "Checklist before was empty");

		String newMarkup = change1.getNewValue();
		assertNotNull(newMarkup, "Wiki Checklist after change");

		JsonNode checklistAfter = ChecklistPlugin.unwrapChecklist(newMarkup);
		assertNotNull(checklistAfter, "Checklist after change");
		assertTrue(checklistAfter.isArray(), "Checklist after is array");
		assertEquals(checklistAfter.size(), 2, "Checklist after size");

		JsonNode header = checklistAfter.get(0);
		assertFalse(getBoolean(header, OPTION), "Item is global option");
		assertTrue(getBoolean(header, HEADER), "Item is header");
		assertEquals(getString(header, NAME), "Deployment Tasks", "Header name");

		JsonNode itemAfter = checklistAfter.get(1);
		assertFalse(getBoolean(itemAfter, OPTION), "Item is global option");
		assertFalse(getBoolean(itemAfter, HEADER), "Item is header");
		assertEquals(getString(itemAfter, NAME), "Build release", "New Item name");
		assertTrue(getBoolean(itemAfter, MANDATORY), "New Item is mandatory");
		assertFalse(getBoolean(itemAfter, CHECKED), "New Item is checked");


		TrackerItemHistoryConfiguration change2 = new TrackerItemHistoryConfiguration(item, Integer.valueOf(2), field, null, null);
		change2.setOldValueObject(adapter.getItemChanges("1) [Mandatory] Build release", controller));
		change2.setNewValueObject(adapter.getItemChanges("1) [Modified, Optional] Deploy release", controller));

		adapter.buildTrackerItemHistoryConfiguration(tracker, item, change2, importer, statistics);

		// Old value of next version must be new value of previous version
		assertEquals(change2.getOldValue(), change1.getNewValue());

		newMarkup = change2.getNewValue();
		assertNotNull(newMarkup, "Wiki Checklist after change");

		checklistAfter = ChecklistPlugin.unwrapChecklist(newMarkup);
		assertNotNull(checklistAfter, "Checklist after change");
		assertTrue(checklistAfter.isArray(), "Checklist after is array");
		assertEquals(checklistAfter.size(), 2, "Checklist after size");

		itemAfter = checklistAfter.get(1);
		assertFalse(getBoolean(itemAfter, OPTION), "Item is global option");
		assertFalse(getBoolean(itemAfter, HEADER), "Item is header");
		assertEquals(getString(itemAfter, NAME), "Deploy release", "Updated Item name");
		assertFalse(getBoolean(itemAfter, MANDATORY), "New Item is mandatory");
		assertFalse(getBoolean(itemAfter, CHECKED), "New Item is checked");


		TrackerItemHistoryConfiguration change3 = new TrackerItemHistoryConfiguration(item, Integer.valueOf(3), field, null, null);
		change3.setOldValueObject(adapter.getItemChanges("1) [Optional] Deploy release", controller));
		change3.setNewValueObject(adapter.getItemChanges("1) [Removed]", controller));

		adapter.buildTrackerItemHistoryConfiguration(tracker, item, change3, importer, statistics);

		// Old value of next version must be new value of previous version
		assertEquals(change3.getOldValue(), change2.getNewValue());

		newMarkup = change3.getNewValue();
		assertNotNull(newMarkup, "Wiki Checklist after change");

		checklistAfter = ChecklistPlugin.unwrapChecklist(newMarkup);
		assertNotNull(checklistAfter, "Checklist after change");
		assertTrue(checklistAfter.isArray(), "Checklist after is array");
		assertEquals(checklistAfter.size(), 1, "Checklist after size");

		header = checklistAfter.get(0);
		assertFalse(getBoolean(header, OPTION), "Item is global option");
		assertTrue(getBoolean(header, HEADER), "Item is header");
		assertEquals(getString(header, NAME), "Deployment Tasks", "Header name");

		adapter.resetChecklist(item, field, importer);
	}


}
