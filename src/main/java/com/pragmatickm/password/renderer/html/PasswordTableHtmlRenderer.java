/*
 * pragmatickm-password-renderer-html - Passwords rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of pragmatickm-password-renderer-html.
 *
 * pragmatickm-password-renderer-html is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * pragmatickm-password-renderer-html is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with pragmatickm-password-renderer-html.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pragmatickm.password.renderer.html;

import com.aoindustries.html.PalpableContent;
import com.aoindustries.html.TABLE_c;
import com.aoindustries.html.TBODY_c;
import com.aoindustries.html.TD_c;
import com.aoindustries.html.THEAD_c;
import com.aoindustries.html.TR_c;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.URIEncoder;
import com.aoindustries.servlet.http.HttpServletUtil;
import com.pragmatickm.password.model.Password;
import com.pragmatickm.password.model.PasswordTable;
import com.semanticcms.core.controller.CapturePage;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.renderer.html.HtmlRenderer;
import com.semanticcms.core.renderer.html.LinkRenderer;
import com.semanticcms.core.renderer.html.PageIndex;
import com.semanticcms.core.servlet.ServletElementContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class PasswordTableHtmlRenderer {

	public static <__ extends PalpableContent<__>> void writePasswordTable(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		__ content,
		PasswordTable passwordTable,
		Iterable<? extends Password> passwords,
		Object style
	) throws IOException, ServletException {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		HtmlRenderer htmlRenderer = HtmlRenderer.getInstance(servletContext);
		PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
		// Combine passwords from both attribute and body
		List<Password> allPasswords = new ArrayList<>();
		if(passwords != null) {
			for(Password password : passwords) {
				allPasswords.add(password);
			}
		}
		for(Element childElement : passwordTable.getChildElements()) {
			if(childElement instanceof Password) allPasswords.add((Password)childElement);
		}

		// Find which columns need to be displayed
		boolean hasHref = false;
		Set<String> uniqueCustomFields = new LinkedHashSet<>();
		boolean hasUsername = false;
		boolean hasSecretQuestion = false;
		for(Password password : allPasswords) {
			if(password.getHref()!=null) hasHref = true;
			uniqueCustomFields.addAll(password.getCustomFields().keySet());
			if(password.getUsername()!=null) hasUsername = true;
			if(!password.getSecretQuestions().isEmpty()) hasSecretQuestion = true;
		}
		int colCount = 1;
		if(hasHref) colCount++;
		colCount += uniqueCustomFields.size();
		if(hasUsername) colCount++;
		if(hasSecretQuestion) colCount += 2;
		// Print the table
		String id = passwordTable.getId();
		try (
			TABLE_c<__> table = content.table()
				.id((id == null) ? null : idAttr -> PageIndex.appendIdInPage(
					pageIndex,
					passwordTable.getPage(),
					id,
					idAttr
				))
				.clazz("ao-grid", "pragmatickm-password")
				.style(style)
			._c()
		) {
			try (THEAD_c<TABLE_c<__>> thead = table.thead_c()) {
				assert colCount >= 1;
				final String header = passwordTable.getHeader();
				if(header != null) {
					try (TR_c<THEAD_c<TABLE_c<__>>> tr = thead.tr_c()) {
						tr.th().clazz("pragmatickm-password-header").colspan(colCount).__(th -> th
							.div__(header)
						);
					}
				}
				if(colCount > 1) {
					try (TR_c<THEAD_c<TABLE_c<__>>> tr = thead.tr_c()) {
						if(hasHref) {
							tr.th__("Site");
						}
						for(String customField : uniqueCustomFields) {
							tr.th__(customField);
						}
						if(hasUsername) {
							tr.th__("Username");
						}
						tr.th__("Password");
						if(hasSecretQuestion) {
							tr.th__("Secret Question")
							.th__("Secret Answer");
						}
					}
				}
			}
			try (TBODY_c<TABLE_c<__>> tbody = table.tbody_c()) {
				// Group like custom values into rowspan
				int hrefRowsLeft = 0;
				Map<String,Integer> customValueRowsLeft = new HashMap<>();
				for(int pwIndex = 0, pwSize = allPasswords.size(); pwIndex < pwSize; pwIndex++) {
					Password password = allPasswords.get(pwIndex);
					Map<String,String> securityQuestions = password.getSecretQuestions();
					int rowSpan = securityQuestions.isEmpty() ? 1 : securityQuestions.size();
					Iterator<Map.Entry<String,String>> securityQuestionIter = securityQuestions.entrySet().iterator();
					for(int row = 0; row < rowSpan; row++) {
						try(TR_c<TBODY_c<TABLE_c<__>>> tr = tbody.tr_c()) {
							if(row == 0) {
								if(hasHref) {
									if(hrefRowsLeft>0) {
										// Skip row and decrement counter
										hrefRowsLeft--;
									} else {
										// Look ahead for the total number of values to group
										String href = password.getHref();
										assert hrefRowsLeft == 0;
										for(int aheadIndex = pwIndex + 1; aheadIndex < pwSize; aheadIndex++) {
											Password aheadPw = allPasswords.get(aheadIndex);
											String ahead = aheadPw.getHref();
											if(Objects.equals(href, ahead)) {
												hrefRowsLeft += Math.max(1, aheadPw.getSecretQuestions().size());
											} else {
												break;
											}
										}
										int totalRowSpan = rowSpan + hrefRowsLeft;
										assert totalRowSpan >= 1;
										try (TD_c<TR_c<TBODY_c<TABLE_c<__>>>> td = tr.td().rowspan(totalRowSpan)._c()) {
											if(href != null) {
												td.a(HttpServletUtil.buildURL(request, response, href, null, false, false)).__(href);
											}
										}
									}
								}
								Map<String,Password.CustomField> customFields = password.getCustomFields();
								for(String customField : uniqueCustomFields) {
									Integer rowsLeft = customValueRowsLeft.get(customField);
									if(rowsLeft != null) {
										// Skip row and decrement counter
										customValueRowsLeft.put(
											customField,
											rowsLeft == 1 ? null : (rowsLeft - 1)
										);
									} else {
										// Look ahead for the total number of custom values to group
										Password.CustomField value = customFields.get(customField);
										int newRowsLeft = 0;
										for(int aheadIndex = pwIndex + 1; aheadIndex < pwSize; aheadIndex++) {
											Password aheadPw = allPasswords.get(aheadIndex);
											Password.CustomField ahead = aheadPw.getCustomFields().get(customField);
											if(Objects.equals(value, ahead)) {
												newRowsLeft += Math.max(1, aheadPw.getSecretQuestions().size());
											} else {
												break;
											}
										}
										if(newRowsLeft > 0) {
											customValueRowsLeft.put(
												customField,
												newRowsLeft
											);
										}
										int totalRowSpan = rowSpan + newRowsLeft;
										assert totalRowSpan >= 1;
										tr.td().rowspan(totalRowSpan).__(td -> {
											if(value != null) {
												final PageRef pageRef = value.getPageRef();
												final String element = value.getElement();
												if(pageRef != null) {
													// TODO: Capture all the pages above in a batch, allows for concurrent capture
													// Get the target page even when value is also provided to validate correct page linking
													final BookRef bookRef = pageRef.getBookRef();
													Page targetPage =
														semanticCMS.getBook(bookRef).isAccessible()
														? CapturePage.capturePage(
															servletContext,
															request,
															response,
															pageRef,
															element==null ? CaptureLevel.PAGE : CaptureLevel.META
														)
														: null
													;
													// Find the element
													Element targetElement;
													if(element != null && targetPage != null) {
														targetElement = targetPage.getElementsById().get(element);
														if(targetElement == null) throw new ServletException("Element not found in target page: " + element);
														if(targetPage.getGeneratedIds().contains(element)) throw new ServletException("Not allowed to link to a generated element id, set an explicit id on the target element: " + element);
													} else {
														targetElement = null;
													}

													Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);

													StringBuilder href = new StringBuilder();
													if(element == null) {
														if(index != null) {
															href.append('#');
															URIEncoder.encodeURIComponent(
																PageIndex.getRefId(
																	index,
																	null
																),
																href
															);
														} else {
															URIEncoder.encodeURI(request.getContextPath(), href);
															URIEncoder.encodeURI(bookRef.getPrefix(), href);
															URIEncoder.encodeURI(pageRef.getPath().toString(), href);
														}
													} else {
														if(index != null) {
															href.append('#');
															URIEncoder.encodeURIComponent(
																PageIndex.getRefId(
																	index,
																	element
																),
																href
															);
														} else {
															URIEncoder.encodeURI(request.getContextPath(), href);
															URIEncoder.encodeURI(bookRef.getPrefix(), href);
															URIEncoder.encodeURI(pageRef.getPath().toString(), href);
															href.append('#');
															URIEncoder.encodeURIComponent(element, href);
														}
													}
													td
														.a(response.encodeURL(href.toString()))
														.clazz(targetElement == null ? null : htmlRenderer.getLinkCssClass(targetElement))
													.__(a -> {
														if(value.getValue() != null) {
															a.text(value.getValue());
														} else {
															if(targetElement != null) {
																a.text(targetElement);
															} else if(targetPage != null) {
																a.text(targetPage.getTitle());
															} else {
																a.text(text -> LinkRenderer.writeBrokenPath(pageRef, text));
															}
														}
														if(index != null) {
															a.sup__(sup -> sup
																.text('[').text(index + 1).text(']')
															);
														}
													});
												} else {
													td.text(value.getValue());
												}
											}
										});
									}
								}
								assert rowSpan >= 1;
								if(hasUsername) {
									tr.td().rowspan(rowSpan).__(password.getUsername());
								}
								String pid = password.getId();
								tr.td().rowspan(rowSpan).__(td -> td
									.span()
										.id((pid == null) ? null : idAttr -> PageIndex.appendIdInPage(
											pageIndex,
											passwordTable.getPage(),
											pid,
											idAttr
										))
										.clazz(htmlRenderer.getLinkCssClass(password))
									.__(password.getPassword())
								);
							}
							if(hasSecretQuestion) {
								Map.Entry<String,String> entry = securityQuestionIter.hasNext() ? securityQuestionIter.next() : null;
								tr.td__(entry == null ? null : entry.getKey())
								.td__(entry == null ? null : entry.getValue());
							}
						}
					}
				}
				BufferResult body = passwordTable.getBody();
				if(body.getLength() > 0) {
					assert colCount >= 1;
					try (TR_c<TBODY_c<TABLE_c<__>>> tr = tbody.tr_c()) {
						tr.td().clazz("pragmatickm-password-body").colspan(colCount).__(td ->
							body.writeTo(new NodeBodyWriter(passwordTable, td.getDocument().out, new ServletElementContext(servletContext, request, response)))
						);
					}
				}
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private PasswordTableHtmlRenderer() {
	}
}
