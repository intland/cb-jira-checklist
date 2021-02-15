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

import org.testng.annotations.Test;

import static org.testng.Assert.*;

import static com.intland.codebeamer.extensions.jira.ChecklistForJiraMarkup.*;

/**
 * Tests for the special {@link ChecklistForJiraMarkup} converter between
 * <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1951662210/Using+special+formatting">Checklist for JIRA Markup<a>
 * and codeBeamer Wiki Markup
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Test
public class ChecklistForJiraMarkupNGTests {

	/**
	 * Test the conversion of <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1951662210/Using+special+formatting">Checklist for JIRA Markup<a>
	 * to codeBeamer Wiki markup
	 */
	public void testChecklist2cb() throws Exception {
		String csMarkup = "# Header1\n## Header2\n### Header3";
		String cbMarkup = checklist2cb(csMarkup);

		assertEquals(cbMarkup, "!1 Header1\n!2 Header2\n!3 Header3", csMarkup);

		csMarkup = "The customer said *what*?\nInvoices **must** be submitted by 5!\n* Option1\n** Option1.1\n* Option2";
		cbMarkup = checklist2cb(csMarkup);

		assertEquals(cbMarkup, "The customer said ''what''?\nInvoices __must__ be submitted by 5!\n* Option1\n** Option1.1\n* Option2", csMarkup);

		csMarkup = "See our [return policy] (http://www.company.com/return-policy).";
		cbMarkup = checklist2cb(csMarkup);

		assertEquals(cbMarkup, "See our [return policy|http://www.company.com/return-policy].", csMarkup);

		csMarkup = "![alt-text](http://www.okapya.com/logo.png)";
		cbMarkup = checklist2cb(csMarkup);

		assertEquals(cbMarkup, "[alt-text|http://www.okapya.com/logo.png]", csMarkup);

		csMarkup = "Code example\n    a + b = c;";
		cbMarkup = checklist2cb(csMarkup);

		assertEquals(cbMarkup, "Code example\n{{{a + b = c;}}}", csMarkup);
	}

	/**
	 * Test the conversion of codeBeamer Wiki markup to
	 * <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1951662210/Using+special+formatting">Checklist for JIRA Markup<a>
	 */
	public void testCBtoChecklist() throws Exception {
		String cbMarkup = "!3 Header3\n!4 Header4\n!5 Header5";
		String csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "### Header3\n#### Header4\n##### Header5", cbMarkup);

		cbMarkup = "The customer said ''what''?\nInvoices __must__ be submitted by 5!\n* Option1\n** Option1.1\n* Option2";
		csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "The customer said *what*?\nInvoices **must** be submitted by 5!\n* Option1\n** Option1.1\n* Option2", cbMarkup);

		cbMarkup = "See our [return policy|http://www.company.com/return-policy].";
		csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "See our [return policy](http://www.company.com/return-policy).", cbMarkup);

		cbMarkup = "[alt-text|http://www.okapya.com/logo.png]";
		csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "![alt-text](http://www.okapya.com/logo.png)", cbMarkup);

		cbMarkup = "[http://www.codebeamer.com/cb-logo.png]";
		csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "![http://www.codebeamer.com/cb-logo.png](http://www.codebeamer.com/cb-logo.png)", cbMarkup);

		cbMarkup = "{{{int max(int a, int b)}}}";
		csMarkup = cb2checklist(cbMarkup);

		assertEquals(csMarkup, "    int max(int a, int b)", cbMarkup);
	}

}
