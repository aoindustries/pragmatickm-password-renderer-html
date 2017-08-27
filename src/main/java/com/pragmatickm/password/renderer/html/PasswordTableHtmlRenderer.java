/*
 * pragmatickm-password-renderer-html - Passwords rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017  AO Industries, Inc.
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
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.lang.ObjectUtils;
import com.aoindustries.servlet.http.LastModifiedServlet;
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
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		Writer out,
		PasswordTable passwordTable,
		Iterable<? extends Password> passwords,
		Object style
	) throws IOException, ServletException {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		HtmlRenderer htmlRenderer = HtmlRenderer.getInstance(servletContext);
		PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
		// Combine passwords from both attribute and body
		List<Password> allPasswords = new ArrayList<Password>();
		if(passwords != null) {
			for(Password password : passwords) allPasswords.add(password);
		}
		for(Element childElement : passwordTable.getChildElements()) {
			if(childElement instanceof Password) allPasswords.add((Password)childElement);
		}

		final String responseEncoding = response.getCharacterEncoding();
		// Find which columns need to be displayed
		boolean hasHref = false;
		Set<String> uniqueCustomFields = new LinkedHashSet<String>();
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
		out.write("<table class=\"thinTable passwordTable\"");
		if(style != null) {
			out.write(" style=\"");
			Coercion.write(style, textInXhtmlAttributeEncoder, out);
			out.write('"');
		}
		out.write(">\n"
				+ "<thead>\n");
		final String header = passwordTable.getHeader();
		if(header != null) {
			out.write("<tr>\n"
					+ "<th class=\"passwordTableHeader\"");
			if(colCount>1) {
				out.write(" colspan=\"");
				encodeTextInXhtmlAttribute(Integer.toString(colCount), out);
				out.write("\"");
			}
			out.write("><div>");
			encodeTextInXhtml(header, out);
			out.write("</div></th>\n"
					+ "</tr>\n");
		}
		if(colCount>1) {
			out.write("<tr>\n");
			if(hasHref) out.write("<th>Site</th>\n");
			for(String customField : uniqueCustomFields) {
				out.write("<th>");
				encodeTextInXhtml(customField, out);
				out.write("</th>\n");
			}
			if(hasUsername) out.write("<th>Username</th>\n");
			out.write("<th>Password</th>\n");
			if(hasSecretQuestion) {
				out.write("<th>Secret Question</th>\n"
						+ "<th>Secret Answer</th>\n");
			}
			out.write("</tr>\n");
		}
		out.write("</thead>\n"
				+ "<tbody>\n");
		// Group like custom values into rowspan
		int hrefRowsLeft = 0;
		Map<String,Integer> customValueRowsLeft = new HashMap<String,Integer>();
		for(int pwIndex=0, pwSize=allPasswords.size(); pwIndex<pwSize; pwIndex++) {
			Password password = allPasswords.get(pwIndex);
			Map<String,String> securityQuestions = password.getSecretQuestions();
			int rowSpan = securityQuestions.isEmpty() ? 1 : securityQuestions.size();
			Iterator<Map.Entry<String,String>> securityQuestionIter = securityQuestions.entrySet().iterator();
			for(int row=0; row<rowSpan; row++) {
				out.write("<tr>\n");
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
								if(ObjectUtils.equals(href, ahead)) {
									hrefRowsLeft += Math.max(1, aheadPw.getSecretQuestions().size());
								} else {
									break;
								}
							}
							out.write("<td");
							int totalRowSpan = rowSpan + hrefRowsLeft;
							if(totalRowSpan>1) {
								out.write(" rowspan=\"");
								out.write(Integer.toString(totalRowSpan));
								out.write('"');
							}
							out.write('>');
							if(href!=null) {
								out.write("<a");
								UrlUtils.writeHref(servletContext, request, response, out, href, null, false, LastModifiedServlet.AddLastModifiedWhen.FALSE);
								out.write(">");
								encodeTextInXhtml(href, out);
								out.write("</a>");
							}
							out.write("</td>\n");
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
								if(ObjectUtils.equals(value, ahead)) {
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
							out.write("<td");
							int totalRowSpan = rowSpan + newRowsLeft;
							if(totalRowSpan>1) {
								out.write(" rowspan=\"");
								out.write(Integer.toString(totalRowSpan));
								out.write('"');
							}
							out.write('>');
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

									out.write("<a href=\"");
									if(element == null) {
										if(index != null) {
											out.write('#');
											PageIndex.appendIdInPage(
												index,
												null,
												new MediaWriter(textInXhtmlAttributeEncoder, out)
											);
										} else {
											encodeTextInXhtmlAttribute(
												response.encodeURL(
													com.aoindustries.net.UrlUtils.encodeUrlPath(
														request.getContextPath() + bookRef.getPrefix() + pageRef.getPath(),
														responseEncoding
													)
												),
												out
											);
										}
									} else {
										if(index != null) {
											out.write('#');
											PageIndex.appendIdInPage(
												index,
												element,
												new MediaWriter(textInXhtmlAttributeEncoder, out)
											);
										} else {
											encodeTextInXhtmlAttribute(
												response.encodeURL(
													com.aoindustries.net.UrlUtils.encodeUrlPath(
														request.getContextPath() + bookRef.getPrefix() + pageRef.getPath() + '#' + element,
														responseEncoding
													)
												),
												out
											);
										}
									}
									out.write('"');
									if(targetElement != null) {
										String linkCssClass = htmlRenderer.getLinkCssClass(targetElement);
										if(linkCssClass != null) {
											out.write(" class=\"");
											encodeTextInXhtmlAttribute(linkCssClass, out);
											out.write('"');
										}
									}
									out.write('>');
									if(value.getValue() != null) {
										encodeTextInXhtml(value.getValue(), out);
									} else {
										if(targetElement != null) {
											encodeTextInXhtml(targetElement.getLabel(), out);
										} else if(targetPage != null) {
											encodeTextInXhtml(targetPage.getTitle(), out);
										} else {
											LinkRenderer.writeBrokenPathInXhtml(pageRef, out);
										}
									}
									if(index != null) {
										out.write("<sup>[");
										encodeTextInXhtml(Integer.toString(index+1), out);
										out.write("]</sup>");
									}
									out.write("</a>");
								} else {
									encodeTextInXhtml(value.getValue(), out);
								}
							}
							out.write("</td>\n");
						}
					}
					if(hasUsername) {
						out.write("<td");
						if(rowSpan>1) {
							out.write(" rowspan=\"");
							out.write(Integer.toString(rowSpan));
							out.write('"');
						}
						out.write('>');
						String username = password.getUsername();
						if(username!=null) {
							encodeTextInXhtml(username, out);
						}
						out.write("</td>\n");
					}
					out.write("<td");
					if(rowSpan>1) {
						out.write(" rowspan=\"");
						out.write(Integer.toString(rowSpan));
						out.write('"');
					}
					out.write("><span");
					String id = password.getId();
					if(id != null) {
						final Page currentPage = passwordTable.getPage();
						if(currentPage != null) {
							out.write(" id=\"");
							PageIndex.appendIdInPage(
								pageIndex,
								currentPage,
								id,
								new MediaWriter(textInXhtmlAttributeEncoder, out)
							);
							out.write('"');
						}
					}
					String linkCssClass = htmlRenderer.getLinkCssClass(password);
					if(linkCssClass != null) {
						out.write(" class=\"");
						encodeTextInXhtmlAttribute(linkCssClass, out);
						out.write('"');
					}
					out.write('>');
					encodeTextInXhtml(password.getPassword(), out);
					out.write("</span></td>\n");
				}
				if(hasSecretQuestion) {
					Map.Entry<String,String> entry = securityQuestionIter.hasNext() ? securityQuestionIter.next() : null;
					out.write("<td>");
					if(entry!=null) encodeTextInXhtml(entry.getKey(), out);
					out.write("</td>\n"
							+ "<td>");
					if(entry!=null) encodeTextInXhtml(entry.getValue(), out);
					out.write("</td>\n");
				}
				out.write("</tr>\n");
			}
		}
		BufferResult body = passwordTable.getBody();
		if(body.getLength() > 0) {
			out.write("<tr><td class=\"passwordTableBody\"");
			if(colCount>1) {
				out.write(" colspan=\"");
				encodeTextInXhtmlAttribute(Integer.toString(colCount), out);
				out.write("\"");
			}
			out.write('>');
			body.writeTo(new NodeBodyWriter(passwordTable, out, new ServletElementContext(servletContext, request, response)));
			out.write("</td></tr>\n");
		}
		out.write("</tbody>\n"
				+ "</table>");
	}

	/**
	 * Make no instances.
	 */
	private PasswordTableHtmlRenderer() {
	}
}
