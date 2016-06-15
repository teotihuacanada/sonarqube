/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.platform;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.jruby.rack.RackEnvironment;
import org.jruby.rack.servlet.RequestCapture;
import org.jruby.rack.servlet.ResponseCapture;
import org.sonar.api.utils.log.Loggers;

public class RubyRailsFilter extends org.jruby.rack.RackFilter {

  @Override
  protected boolean isDoDispatch(RequestCapture request, ResponseCapture response, FilterChain chain, RackEnvironment env) throws IOException, ServletException {
    String path = request.getServletPath();
    if (path.startsWith("/css/") ||
      path.startsWith("/fonts/") ||
      path.startsWith("/images/") ||
      path.startsWith("/static/") ||
      path.startsWith("/js/")) {
      Loggers.get(getClass()).debug(" --> NO RAILS FOR {}", request.getRequestURI());
      chain.doFilter(request, response);
      return false;
    }
    Loggers.get(getClass()).debug(" --> RAILS FOR {}", request.getRequestURI());
    return true;

  }
}
