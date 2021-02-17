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

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;

import com.intland.codebeamer.utils.RegexpUtils;
import com.intland.codebeamer.utils.RegexpUtils.IReplacementLogic;


/**
 * A special converter between <a href="https://okapya.atlassian.net/wiki/spaces/CHKDOC5/pages/1951662210/Using+special+formatting">Checklist for JIRA Markup<a>
 * and codeBeamer Wiki Markup
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
public class ChecklistForJiraMarkup {
	public static final Logger logger = Logger.getLogger(ChecklistForJiraMarkup.class);

	public static final String  CHECKLIST_LINK_EXPR 		= "\\!?\\[(.+?)\\]\\s*\\((.+?)\\)";
	public static final String  CHECKLIST_HEADER_PATTERN	= "(?<=^|\\n)[#]+[ \t]+";
	public static final String  CHECKLIST_ITALIC_PATTERN	= "(?<=^|\\s)\\*([^\\s*][^*]*)\\*(?=$|[\\s.:;,_!?)}\\]\"%/~+-])";
	public static final String  CHECKLIST_BOLD_PATTERN		= "(?<=^|\\s)\\*\\*([^\\s*][^*]*)\\*\\*(?=$|[\\s.:;,_!?)}\\]\"%/~+-])";
	public static final String  CHECKLIST_CODE_PATTERN		= "(?<=^|\\n)([ ]{4}.+?)(?=(?:$|\\r|\\n))";

	public static final Pattern CHECKLIST_MARKUP_PATTERN	= Pattern.compile(StringUtils.join(Arrays.asList(
																CHECKLIST_LINK_EXPR,
																CHECKLIST_HEADER_PATTERN,
																CHECKLIST_ITALIC_PATTERN,
																CHECKLIST_BOLD_PATTERN,
																CHECKLIST_CODE_PATTERN
															  ), '|'), Pattern.DOTALL);


	public static final IReplacementLogic CHECKLIST_2_CB = new IReplacementLogic() {
		@Override
		public String getReplacement(Matcher matcher) {
			String pattern = matcher.group();
			String trimmed = pattern.trim();
			String result  = pattern;

			if (StringUtils.startsWithAny(pattern, "[", "![") && StringUtils.endsWith(pattern, ")")) { // (Image) Link
				String alias = matcher.group(1);
				String link  = matcher.group(2);

				if (StringUtils.isNotBlank(alias)) {
					result = "[" + alias + "|" + link + "]";
				} else {
					result = "[" + link + "]";
				}
			} else if (StringUtils.startsWith(trimmed, "#")) { // Heading
				result = "!" + trimmed.length() + " ";
			} else if (StringUtils.startsWith(trimmed, "**")) { // Bold
				result = "__" + RegexpUtils.replaceAllRegexpMatches(matcher.group(4), CHECKLIST_MARKUP_PATTERN, this) + "__";
			} else if (StringUtils.startsWith(trimmed, "*")) { // Italic
				result = "''" + RegexpUtils.replaceAllRegexpMatches(matcher.group(3), CHECKLIST_MARKUP_PATTERN, this) + "''";
			} else if (StringUtils.startsWith(pattern, "    ")) { // Code
				result = "{{{" + matcher.group(5).substring(4) + "}}}";
			}

			return Matcher.quoteReplacement(result);
		}
	};

	public static String checklist2cb(String markup) {
		String result = markup;

		if (StringUtils.isNotBlank(markup)) {
			try {
				result = RegexpUtils.replaceAllRegexpMatches(result, CHECKLIST_MARKUP_PATTERN, CHECKLIST_2_CB);
			} catch (Throwable ex) {
				logger.warn("checklist2cb(" + markup + ") failed", ex);
			}
		}

		return result;
	}


	public static final String  CB_LINK_EXPR 		= "\\[(?:(.+?)\\|)?(.+?)\\]";
	public static final String  CB_HEADER_PATTERN	= "(?<=^|\\n)\\!([1-6])";
	public static final String  CB_ITALIC_PATTERN	= "''(.+?)''";
	public static final String  CB_BOLD_PATTERN 	= "__(.+?)__";
	public static final String  CB_CODE_PATTERN     = "(?<=^|\\n)\\{\\{\\{(.+?)\\}\\}\\}";

	public static final Pattern CB_MARKUP_PATTERN	= Pattern.compile(StringUtils.join(Arrays.asList(
														CB_LINK_EXPR,
														CB_HEADER_PATTERN,
														CB_ITALIC_PATTERN,
														CB_BOLD_PATTERN,
														CB_CODE_PATTERN
													  ), '|'), Pattern.DOTALL);


	public static final IReplacementLogic CB_2_CHECKLIST = new IReplacementLogic() {
		@Override
		public String getReplacement(Matcher matcher) {
			String pattern = matcher.group();
			String trimmed = pattern.trim();
			String result  = pattern;

			if (StringUtils.startsWith(pattern, "[") && StringUtils.endsWith(pattern, "]")) { // (Image) Link
				String  alias = matcher.group(1);
				String  link  = matcher.group(2);
				boolean img   = StringUtils.endsWithAny(link, ".jpg", "jpeg", ".gif", ".png", ".bmp");

				result = (img ? "![" : "[") + StringUtils.defaultIfBlank(alias, link) + "](" + link + ")";
			} else if (StringUtils.startsWith(pattern, "!")) { // Heading
				result = StringUtils.repeat('#', NumberUtils.toInt(matcher.group(3)));
			} else if (StringUtils.startsWith(trimmed, "__")) { // bold
				result = "**" + RegexpUtils.replaceAllRegexpMatches(matcher.group(5), CB_MARKUP_PATTERN, this) + "**";
			} else if (StringUtils.startsWith(trimmed, "''")) { // Italic
				result = "*" + RegexpUtils.replaceAllRegexpMatches(matcher.group(4), CB_MARKUP_PATTERN, this) + "*";
			} else if (StringUtils.startsWith(trimmed, "{{{")) { // Code
				result = "    " + matcher.group(6);
			}

			return Matcher.quoteReplacement(result);
		}
	};


	public static String cb2checklist(String markup) {
		String result = markup;

		if (StringUtils.isNotBlank(markup)) {
			try {
				result = RegexpUtils.replaceAllRegexpMatches(result, CB_MARKUP_PATTERN, CB_2_CHECKLIST);
			} catch (Throwable ex) {
				logger.warn("cb2checklist2(" + markup + ") failed", ex);
			}
		}

		return result;
	}


}
