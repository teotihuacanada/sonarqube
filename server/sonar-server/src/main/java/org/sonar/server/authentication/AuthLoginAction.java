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

package org.sonar.server.authentication;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.elasticsearch.common.Strings.isNullOrEmpty;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sonar.api.web.ServletFilter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.user.ServerUserSession;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.UserSession;

public class AuthLoginAction extends ServletFilter {

  private static final String POST = "POST";

  private final ThreadLocalUserSession threadLocalUserSession;
  private final DbClient dbClient;
  private final JwtHttpHandler jwtHttpHandler;
  private final ExternalAuthenticator externalAuthenticator;
  private final LocalAuthenticator localAuthenticator;

  public AuthLoginAction(ThreadLocalUserSession threadLocalUserSession, DbClient dbClient, JwtHttpHandler jwtHttpHandler, ExternalAuthenticator externalAuthenticator,
    LocalAuthenticator localAuthenticator) {
    this.threadLocalUserSession = threadLocalUserSession;
    this.dbClient = dbClient;
    this.jwtHttpHandler = jwtHttpHandler;
    this.externalAuthenticator = externalAuthenticator;
    this.localAuthenticator = localAuthenticator;
  }

  @Override
  public UrlPattern doGetPattern() {
    return UrlPattern.create("/api/auth/login");
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    if (!request.getMethod().equals(POST)) {
      response.setStatus(HTTP_BAD_REQUEST);
      return;
    }

    String login = request.getParameter("login");
    String password = request.getParameter("password");
    if (isNullOrEmpty(login) || isNullOrEmpty(password)) {
      returnUnauthorized(response);
      return;
    }

    DbSession dbSession = dbClient.openSession(false);
    try {
      UserDto userDto = authenticate(login, password, request);
      List<GroupDto> userGroups = dbClient.groupDao().selectByUserLogin(dbSession, userDto.getLogin());
      UserSession session = new ServerUserSession(dbClient.authorizationDao(), dbClient.resourceDao())
        .setLogin(login)
        .setName(userDto.getName())
        .setUserId(userDto.getId().intValue())
        .setUserGroups(userGroups.stream().map(GroupDto::getName).toArray(String[]::new));
      threadLocalUserSession.set(session);
      jwtHttpHandler.generateToken(userDto.getLogin(), response);
      request.getSession().setAttribute("user_id", userDto.getId());

      chain.doFilter(request, response);
    } catch (UnauthorizedException e) {
      response.setStatus(e.httpCode());
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private UserDto authenticate(String login, String password, HttpServletRequest request) {
    Optional<UserDto> userDto = localAuthenticator.authenticate(login, password);
    if (userDto.isPresent()) {
      return userDto.get();
    } else if (externalAuthenticator.isExternalAuthenticationUsed()) {
      return externalAuthenticator.authenticate(login, password, request);
    }
    throw new UnauthorizedException();
  }

  private void returnUnauthorized(HttpServletResponse response) {
    response.setStatus(HTTP_UNAUTHORIZED);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // Nothing to do
  }

  @Override
  public void destroy() {
    // Nothing to do
  }
}
