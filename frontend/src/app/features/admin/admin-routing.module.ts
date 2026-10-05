import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminAuthorsPageComponent } from './admin-authors-page/admin-authors-page.component';
import { AdminCategoriesPageComponent } from './admin-categories-page/admin-categories-page.component';
import { AdminPlaceholderPageComponent } from './admin-placeholder-page/admin-placeholder-page.component';
import { AdminPublishersPageComponent } from './admin-publishers-page/admin-publishers-page.component';
import { AdminShellComponent } from './admin-shell/admin-shell.component';

const routes: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'publishers' },
      { path: 'publishers', component: AdminPublishersPageComponent },
      { path: 'authors', component: AdminAuthorsPageComponent },
      { path: 'categories', component: AdminCategoriesPageComponent },
      {
        path: 'books',
        component: AdminPlaceholderPageComponent,
        data: { title: 'Kitaplar' },
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
