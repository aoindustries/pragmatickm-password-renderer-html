/*
 * pragmatickm-password-renderer-html - Passwords rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.encoding.Coercion;
import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import com.aoindustries.html.Html;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.EmptyURIParameters;
import com.aoindustries.net.URIEncoder;
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
import com.semanticcms.core.renderer.html.UrlUtils;
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

	public static void writePasswordTable(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
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
			for(Password password : passwords) allPasswords.add(password);
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
		html.out.write("<table");
		String id = passwordTable.getId();
		if(id != null) {
			final Page currentPage = passwordTable.getPage();
			if(currentPage != null) {
				html.out.write(" id=\"");
				PageIndex.appendIdInPage(
					pageIndex,
					currentPage,
					id,
					new MediaWriter(textInXhtmlAttributeEncoder, html.out)
				);
				html.out.write('"');
			}
		}
		html.out.write(" class=\"thinTable passwordTable\"");
		if(style != null) {
			html.out.write(" style=\"");
			Coercion.write(style, textInXhtmlAttributeEncoder, html.out);
			html.out.write('"');
		}
		html.out.write(">\n"
			+ "<thead>\n");
		final String header = passwordTable.getHeader();
		if(header != null) {
			html.out.write("<tr>\n"
				+ "<th class=\"passwordTableHeader\"");
			if(colCount>1) {
				html.out.write(" colspan=\"");
				encodeTextInXhtmlAttribute(Integer.toString(colCount), html.out);
				html.out.write('"');
			}
			html.out.write("><div>");
			html.text(header);
			html.out.write("</div></th>\n"
				+ "</tr>\n");
		}
		if(colCount>1) {
			html.out.write("<tr>\n");
			if(hasHref) html.out.write("<th>Site</th>\n");
			for(String customField : uniqueCustomFields) {
				html.out.write("<th>");
				html.text(customField);
				html.out.write("</th>\n");
			}
			if(hasUsername) html.out.write("<th>Username</th>\n");
			html.out.write("<th>Password</th>\n");
			if(hasSecretQuestion) {
				html.out.write("<th>Secret Question</th>\n"
					+ "<th>Secret Answer</th>\n");
			}
			html.out.write("</tr>\n");
		}
		html.out.write("</thead>\n"
			+ "<tbody>\n");
		// Group like custom values into rowspan
		int hrefRowsLeft = 0;
		Map<String,Integer> customValueRowsLeft = new HashMap<>();
		for(int pwIndex=0, pwSize=allPasswords.size(); pwIndex<pwSize; pwIndex++) {
			Password password = allPasswords.get(pwIndex);
			Map<String,String> securityQuestions = password.getSecretQuestions();
			int rowSpan = securityQuestions.isEmpty() ? 1 : securityQuestions.size();
			Iterator<Map.Entry<String,String>> securityQuestionIter = securityQuestions.entrySet().iterator();
			for(int row=0; row<rowSpan; row++) {
				html.out.write("<tr>\n");
				if(row==0) {
					if(hasHref) {
						if(hrefRowsLeft>0) {
							// Skip row and decrement counter
							hrefRowsLeft--;
						} else {
							// Look ahead for the total number of values to group
							String href = password.getHref();
							assert hrefRowsLeft == 0;
							for(int aheadIndex=pwIndex+1; aheadIndex<pwSize; aheadIndex++) {
								Password aheadPw = allPasswords.get(aheadIndex);
								String ahead = aheadPw.getHref();
								if(Objects.equals(href, ahead)) {
									hrefRowsLeft += Math.max(1, aheadPw.getSecretQuestions().size());
								} else {
									break;
								}
							}
							html.out.write("<td");
							int totalRowSpan = rowSpan + hrefRowsLeft;
							if(totalRowSpan>1) {
								html.out.write(" rowspan=\"");
								encodeTextInXhtmlAttribute(Integer.toString(totalRowSpan), html.out);
								html.out.write('"');
							}
							html.out.write('>');
							if(href!=null) {
								html.out.write("<a");
								UrlUtils.writeHref(request, response, html.out, href, EmptyURIParameters.getInstance(), false, false);
								html.out.write('>');
								html.text(href);
								html.out.write("</a>");
							}
							html.out.write("</td>\n");
						}
					}
					Map<String,Password.CustomField> customFields = password.getCustomFields();
					for(String customField : uniqueCustomFields) {
						Integer rowsLeft = customValueRowsLeft.get(customField);
						if(rowsLeft!=null) {
							// Skip row and decrement counter
							customValueRowsLeft.put(
								customField,
								rowsLeft==1 ? null : (rowsLeft - 1)
							);
						} else {
							// Look ahead for the total number of custom values to group
							Password.CustomField value = customFields.get(customField);
							int newRowsLeft = 0;
							for(int aheadIndex=pwIndex+1; aheadIndex<pwSize; aheadIndex++) {
								Password aheadPw = allPasswords.get(aheadIndex);
								Password.CustomField ahead = aheadPw.getCustomFields().get(customField);
								if(Objects.equals(value, ahead)) {
									newRowsLeft += Math.max(1, aheadPw.getSecretQuestions().size());
								} else {
									break;
								}
							}
							if(newRowsLeft>0) {
								customValueRowsLeft.put(
									customField,
									newRowsLeft
								);
							}
							html.out.write("<td");
							int totalRowSpan = rowSpan + newRowsLeft;
							if(totalRowSpan>1) {
								html.out.write(" rowspan=\"");
								encodeTextInXhtmlAttribute(Integer.toString(totalRowSpan), html.out);
								html.out.write('"');
							}
							html.out.write('>');
							if(value!=null) {
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

									html.out.write("<a href=\"");
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
									encodeTextInXhtmlAttribute(
										response.encodeURL(
											href.toString()
										),
										html.out
									);
									html.out.write('"');
									if(targetElement != null) {
										String linkCssClass = htmlRenderer.getLinkCssClass(targetElement);
										if(linkCssClass != null) {
											html.out.write(" class=\"");
											encodeTextInXhtmlAttribute(linkCssClass, html.out);
											html.out.write('"');
										}
									}
									html.out.write('>');
									if(value.getValue() != null) {
										html.text(value.getValue());
									} else {
										if(targetElement != null) {
											html.text(targetElement.getLabel());
										} else if(targetPage != null) {
											html.text(targetPage.getTitle());
										} else {
											LinkRenderer.writeBrokenPathInXhtml(pageRef, html.out);
										}
									}
									if(index != null) {
										html.out.write("<sup>[");
										html.text(index + 1);
										html.out.write("]</sup>");
									}
									html.out.write("</a>");
								} else {
									html.text(value.getValue());
								}
							}
							html.out.write("</td>\n");
						}
					}
					if(hasUsername) {
						html.out.write("<td");
						if(rowSpan>1) {
							html.out.write(" rowspan=\"");
							encodeTextInXhtmlAttribute(Integer.toString(rowSpan), html.out);
							html.out.write('"');
						}
						html.out.write('>');
						String username = password.getUsername();
						if(username!=null) {
							html.text(username);
						}
						html.out.write("</td>\n");
					}
					html.out.write("<td");
					if(rowSpan>1) {
						html.out.write(" rowspan=\"");
						encodeTextInXhtmlAttribute(Integer.toString(rowSpan), html.out);
						html.out.write('"');
					}
					html.out.write("><span");
					id = password.getId();
					if(id != null) {
						final Page currentPage = passwordTable.getPage();
						if(currentPage != null) {
							html.out.write(" id=\"");
							PageIndex.appendIdInPage(
								pageIndex,
								currentPage,
								id,
								new MediaWriter(textInXhtmlAttributeEncoder, html.out)
							);
							html.out.write('"');
						}
					}
					String linkCssClass = htmlRenderer.getLinkCssClass(password);
					if(linkCssClass != null) {
						html.out.write(" class=\"");
						encodeTextInXhtmlAttribute(linkCssClass, html.out);
						html.out.write('"');
					}
					html.out.write('>');
					html.text(password.getPassword());
					html.out.write("</span></td>\n");
				}
				if(hasSecretQuestion) {
					Map.Entry<String,String> entry = securityQuestionIter.hasNext() ? securityQuestionIter.next() : null;
					html.out.write("<td>");
					if(entry!=null) html.text(entry.getKey());
					html.out.write("</td>\n"
						+ "<td>");
					if(entry!=null) html.text(entry.getValue());
					html.out.write("</td>\n");
				}
				html.out.write("</tr>\n");
			}
		}
		BufferResult body = passwordTable.getBody();
		if(body.getLength() > 0) {
			html.out.write("<tr><td class=\"passwordTableBody\"");
			if(colCount>1) {
				html.out.write(" colspan=\"");
				encodeTextInXhtmlAttribute(Integer.toString(colCount), html.out);
				html.out.write('"');
			}
			html.out.write('>');
			body.writeTo(new NodeBodyWriter(passwordTable, html.out, new ServletElementContext(servletContext, request, response)));
			html.out.write("</td></tr>\n");
		}
		html.out.write("</tbody>\n"
			+ "</table>");
	}

	/**
	 * Make no instances.
	 */
	private PasswordTableHtmlRenderer() {
	}
}
