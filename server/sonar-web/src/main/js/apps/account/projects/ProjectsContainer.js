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
import React from 'react';
import Projects from './Projects';
import { getMyProjects } from '../../../api/components';

export default class ProjectsContainer extends React.Component {
  state = {
    loading: true,
    page: 1,
    query: ''
  };

  componentWillMount () {
    this.loadMore = this.loadMore.bind(this);
    this.search = this.search.bind(this);
    document.querySelector('html').classList.add('dashboard-page');
  }

  componentDidMount () {
    this.mounted = true;
    this.loadProjects();
  }

  componentWillUnmount () {
    this.mounted = false;
    document.querySelector('html').classList.remove('dashboard-page');
  }

  loadProjects (page = this.state.page, query = this.state.query) {
    this.setState({ loading: true });
    const data = { ps: 20 }; // FIXME
    if (page > 1) {
      data.p = page;
    }
    if (query) {
      data.q = query;
    }
    return getMyProjects(data).then(r => {
      const projects = page > 1 ?
          [...this.state.projects, ...r.projects] : r.projects;
      this.setState({
        projects,
        query,
        loading: false,
        page: r.paging.pageIndex,
        total: r.paging.total
      });
    });
  }

  loadMore () {
    return this.loadProjects(this.state.page + 1);
  }

  search (query) {
    return this.loadProjects(1, query);
  }

  render () {
    if (this.state.projects == null) {
      return (
          <div className="text-center">
            <i className="spinner spinner-margin"/>
          </div>
      );
    }

    return (
        <Projects
            projects={this.state.projects}
            total={this.state.total}
            loading={this.state.loading}
            loadMore={this.loadMore}
            search={this.search}/>
    );
  }
}
