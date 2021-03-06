/*
 * pragmatickm-password-renderer-html - Passwords rendered as HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2020, 2021  AO Industries, Inc.
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

import com.aoindustries.html.Union_Palpable_Phrasing;
import com.pragmatickm.password.model.Password;
import com.semanticcms.core.model.ElementContext;
import com.semanticcms.core.renderer.html.HtmlRenderer;
import com.semanticcms.core.renderer.html.PageIndex;
import java.io.IOException;

final public class PasswordHtmlRenderer {

	public static <__ extends Union_Palpable_Phrasing<__>> void writePassword(
		HtmlRenderer htmlRenderer,
		PageIndex pageIndex,
		__ content,
		ElementContext context,
		Password password
	) throws IOException {
		String id = password.getId();
		content.span()
			.id((id == null) ? null : idAttr -> PageIndex.appendIdInPage(
				pageIndex,
				password.getPage(),
				id,
				idAttr
			))
			.clazz(htmlRenderer.getLinkCssClass(password))
		.__(password.getPassword());
	}

	/**
	 * Make no instances.
	 */
	private PasswordHtmlRenderer() {
	}
}
