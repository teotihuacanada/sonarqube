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

import java.util.Optional;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.user.UserUpdater;

public class LocalAuthenticator {

  private final DbClient dbClient;

  public LocalAuthenticator(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  public Optional<UserDto> authenticate(String userLogin, String userPassword) {
    DbSession dbSession = dbClient.openSession(false);
    try {
      // TODO Handle here personal user token ? Or in another class ?

      UserDto userDto = dbClient.userDao().selectActiveUserByLogin(dbSession, userLogin);
      if (userDto == null) {
        throw new UnauthorizedException();
      }

      if (!userDto.isLocal()) {
        return Optional.empty();
      }

      if (!userDto.getCryptedPassword().equals(UserUpdater.encryptPassword(userPassword, userDto.getSalt()))) {
        throw new UnauthorizedException();
      }

      return Optional.of(userDto);
    } finally {
      dbClient.closeSession(dbSession);
    }
  }
}
