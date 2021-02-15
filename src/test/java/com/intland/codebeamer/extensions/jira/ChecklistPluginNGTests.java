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

import static com.intland.codebeamer.controller.AbstractJsonController.jsonMapper;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.DESCRIPTION;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.ID;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.persistence.util.PersistenceUtils.getToday;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.BODY;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.CHECKED;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.END_DATE;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.MANDATORY;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.PINNED;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.PLUGIN_FOOTER;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.PLUGIN_HEADER;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.STATUS;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.decodeIsoDate;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.encodeIsoDate;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.getPriority;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.getStatus;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.prepareChecklist;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.unwrapChecklist;
import static com.intland.codebeamer.extensions.jira.ChecklistPlugin.wrapChecklist;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.Date;
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
import com.intland.codebeamer.persistence.dto.TrackerChoiceOptionDto;
import com.intland.codebeamer.persistence.dto.UserDto;
import com.intland.codebeamer.persistence.dto.base.NamedDto;
import com.intland.codebeamer.servlet.CBPaths;
import com.intland.codebeamer.wiki.CodeBeamerWikiContext;


/**
 * TestNG tests for the {@link ChecklistPlugin}
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Test(groups = {"plugins"})
public class ChecklistPluginNGTests  {

	public static ObjectNode createChecklistItem(Integer id, String name, String description, String status, Date dueDate,
												 boolean pinned, boolean header, boolean mandatory, boolean checked) {
		assertNotNull(name, "Checklist item name required");

		ObjectNode item = jsonMapper.createObjectNode();

		if (id != null) {
			item.set(ID, IntNode.valueOf(id.intValue()));
		}

		item.set(NAME, TextNode.valueOf(name));

		if (description != null) {
			item.set(DESCRIPTION, TextNode.valueOf(description));
		}

		if (status != null) {
			item.set(STATUS, TextNode.valueOf(status));
		}

		if (dueDate != null) {
			item.set(END_DATE, TextNode.valueOf(encodeIsoDate(dueDate)));
		}

		if (pinned) {
			item.set(PINNED, BooleanNode.TRUE);
		}

		if (header) {
			item.set(HEADER, BooleanNode.TRUE);
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
	public void testGetPriority() throws Exception {
		NamedDto priority = getPriority(null);
		assertNull(priority, "Null priority");

		priority = getPriority(Integer.valueOf(0));
		assertNull(priority, "priority=0");

		priority = getPriority(Integer.valueOf(9));
		assertNotNull(priority, "priority=9");
		assertEquals(priority.getId(), Integer.valueOf(5), "priority=9");

		priority = getPriority(" ");
		assertNull(priority, "priority=\" \"");

		priority = getPriority("LOW");
		assertNotNull(priority, "priority=\"LOW\"");
		assertEquals(priority.getId(), Integer.valueOf(4), "priority=\"LOW\"" );
		assertEquals(priority.getName(), "LOW", "priority=\"LOW\"" );

		priority = getPriority(new NamedDto());
		assertNull(priority, "priority={}");

		priority = getPriority(new NamedDto(Integer.valueOf(2), ""));
		assertNotNull(priority, "priority={id:2, name:\"\"}");
		assertEquals(priority.getId(), Integer.valueOf(2), "priority={id:2, name:\"\"}" );
		assertEquals(priority.getName(), "High", "priority={id:2, name:\"\"}" );

		priority = getPriority(new NamedDto(null, "HIGH"));
		assertNotNull(priority, "priority={name:\"HIGH\"}");
		assertEquals(priority.getId(), Integer.valueOf(2), "priority={name:\"HIGH\"}" );
		assertEquals(priority.getName(), "HIGH", "priority={name:\"HIGH\"}");
	}

	@Test
	public void testGetStatus() throws Exception {
		TrackerChoiceOptionDto status = getStatus(null);
		assertNull(status, "Null status");

		status = getStatus(Integer.valueOf(3));
		assertNull(status, "Numeric status");

		status = getStatus("In Progress");
		assertNotNull(status, "In Progress");
		assertEquals(status.getName(), "In Progress", "In Progress name");
		assertEquals(status.getStyle(), "inProgress", "In Progress style");

		status = getStatus("blocked");
		assertNotNull(status, "blocked");
		assertEquals(status.getName(), "Blocked", "blocked name");
		assertEquals(status.getStyle(), "blocked", "blocked style");

		status = getStatus("Undefined");
		assertNotNull(status, "Undefined");
		assertEquals(status.getName(), "Undefined", "Undefined name");
		assertNull(status.getStyle(), "Undefined style");

		status = getStatus(new NamedDto(Integer.valueOf(5), "Resolved"));
		assertNotNull(status, "Resolved");
		assertEquals(status.getId(), Integer.valueOf(5), "Resolved name");
		assertEquals(status.getName(), "Resolved", "Resolved name");
		assertNull(status.getStyle(), "Resolved style");
	}

	@Test
	public void testWrapUnwrapAndPrepareChecklist() throws Exception {
		Date	   tomorrow  = new Date(getToday(1).getTime());
		ArrayNode  checklist = jsonMapper.createArrayNode();
		ObjectNode item      = createChecklistItem(null, "Do something", "Example checklist item", "inProgress", tomorrow, true, false, true, false);

		checklist.add(item);

		String checklistMarkup = wrapChecklist(checklist);
		assertNotNull(checklistMarkup, "Checklist wiki markup");
		assertTrue(checklistMarkup.startsWith(PLUGIN_HEADER), "Checklist markup wrapped into Checklist wiki plugin envelope");
		assertTrue(checklistMarkup.endsWith(PLUGIN_FOOTER), "Checklist markup wrapped into Checklist wiki plugin envelope");

		JsonNode unwrapped = unwrapChecklist(checklistMarkup);
		assertNotNull(unwrapped, "Unwrapped checklist");
		assertTrue(unwrapped.isArray(), "Unwrapped checklist is array of checklist items");
		assertEquals(unwrapped.size(), checklist.size(), "Unwrapped Checklist size");

		JsonNode item_ = unwrapped.get(0);
		assertNotNull(item_, "Unwapped checklist item");
		assertTrue(item_.isObject(), "Unwrapped item is object");
		assertEquals(item_, item, "Unwapped checklist item");

		JsonNode endDate = item_.get(END_DATE);
		assertNotNull(endDate, "Unwapped checklist item endDate");
		assertTrue(endDate.isTextual(), "Unwapped checklist item endDate must be a string");

		Date decodedDate = decodeIsoDate(endDate.asText());
		assertNotNull(decodedDate, "Decoded item endDate");
		assertEquals(decodedDate, tomorrow, "Decoded item endDate must be tomorrow");

		List<Map<String,Object>> prepared = prepareChecklist(unwrapped);
		assertNotNull(prepared, "Prepared checklist");
		assertEquals(prepared.size(), unwrapped.size(), "Prepared checklist size");

		Map<String,Object> item__ = prepared.get(0);
		assertNotNull(item__, "Prepared checklist item");
		assertEquals(item__.get(END_DATE), decodedDate, "Prepared checklist item end date");
	}

	@Test(dependsOnMethods = "testWrapUnwrapAndPrepareChecklist")
	public void testChecklistHtmlRendering() throws Exception {
		CBPaths.getInstance().setCbInstallDir(".");
		CBPaths.getInstance().setCbWebappDir(new File("src/main/web"));
		CodeBeamerWikiContext context = mock(CodeBeamerWikiContext.class);
		UserDto user = mock(UserDto.class);
		when(context.getUser()).thenReturn(user);
		
		Date	   today  		= new Date();
		ArrayNode  checklist 	= jsonMapper.createArrayNode();
		ObjectNode globalHeader = createChecklistItem(Integer.valueOf(1), "!5 Global items", "These are __global__ options", null, null, true, true, false, false);
		ObjectNode globalItem 	= createChecklistItem(Integer.valueOf(2), "Check regulations", "Check regulatory compliance", null, null, true, false, true, false);
		ObjectNode localHeader  = createChecklistItem(Integer.valueOf(4), "!5 Item specific", "These are __local__ items", null, null, false, true, false, false);
		ObjectNode localItem    = createChecklistItem(Integer.valueOf(4), "Design solution", null, "blocked", today, false, false, true, false);

		checklist.add(globalHeader);
		checklist.add(globalItem);
		checklist.add(localHeader);
		checklist.add(localItem);

		ChecklistPlugin plugin = new ChecklistPlugin();

		String html = plugin.execute(context, Collections.singletonMap("_body", BODY.toPrettyJSONString(checklist)));
		assertNotNull(html, "Rendered Checklist HTML");

		Document body = Jsoup.parseBodyFragment(html);
		assertNotNull(body, "Parsed HTML document");

		Element stylesheet = body.select("link[rel=stylesheet][href$=ChecklistPlugin.css]").first();
		assertNotNull(stylesheet, "HTML includes ChecklistPlugin.css");

		Element table = body.select("table.checklist").first();
		assertNotNull(table, "HTML includes <table class=\"checkist\">");

		Element script = body.select("script[type=text/javascript]").first();
		assertNotNull(script, "HTML includes <script type=\"text/javascript\">");

		Elements items = table.select("tr.checklistItem");
		assertEquals(items.size(), checklist.size(), "Number of <tr class=\"checklistItem\">");

		for (int idx = 1; idx <= items.size(); ++idx) {
			Element item     = items.get(idx - 1);
			Element pinned   = item.select("td.checklistItemPinned").first();
			Element header   = item.select("td.checklistHeader").first();
			Element checkbox = item.select("input[type=checkbox]").first();
			Element descLink = item.select("a.checklistItemDescription").first();
			Element status   = item.select("span.checklistItemStatus").first();
			Element dueDate  = item.select("span.checklistItemDue").first();

			if (idx <= 2) {
				 assertNotNull(pinned, "First two items are global items and must be pinned");
			} else {
				 assertNull(pinned, "Other items are local items and must not be pinned");
			}

			if (idx == 1 || idx == 3) {
				assertTrue(item.hasClass("header"), idx + ". item must be header");
				assertNotNull(header, idx + ". row must contain single header cell");
				assertTrue(header.hasText(), "Header must contain text");
				assertNull(checkbox, idx + ". Header row must not contain checkbox");
			} else {
				assertFalse(item.hasClass("header"), idx + ". item must not be header");
				assertNull(header, "Non header row must not contain checklistHeader");
				assertNotNull(checkbox, idx + ". item must have a checkbox");
			}

			// First 3 items must have a description link
			if (idx <= 3) {
				assertNotNull(descLink, idx + ". item must have a description link");
				assertNull(status, idx + ". item must not have a status");
				assertNull(dueDate, idx + ". item must not have a due date");
			} else {
				// "4. item must not have description, but a status and due date instead
				assertNull(descLink, idx + ". item must have a description link");

				assertNotNull(status, idx + ". item must have a status");
				assertTrue(status.hasClass("blocked"), "Status must be blocked");

				assertNotNull(dueDate, idx + ". item must have a due date");
				assertTrue(dueDate.hasClass("dueToday") || dueDate.hasClass("overdue"), "Item must be (over)due today");
			}
		}

	}

}
