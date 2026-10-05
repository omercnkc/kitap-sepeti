import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AdminAuthorsPageComponent } from './admin-authors-page/admin-authors-page.component';
import { AdminBookFormPageComponent } from './admin-book-form-page/admin-book-form-page.component';
import { AdminBooksPageComponent } from './admin-books-page/admin-books-page.component';
import { AdminCategoriesPageComponent } from './admin-categories-page/admin-categories-page.component';
import { AdminNameSlugFormComponent } from './admin-name-slug-form/admin-name-slug-form.component';
import { AdminPublishersPageComponent } from './admin-publishers-page/admin-publishers-page.component';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './admin-shell/admin-shell.component';

@NgModule({
  declarations: [
    AdminShellComponent,
    AdminNameSlugFormComponent,
    AdminPublishersPageComponent,
    AdminAuthorsPageComponent,
    AdminCategoriesPageComponent,
    AdminBooksPageComponent,
    AdminBookFormPageComponent,
  ],
  imports: [SharedModule, AdminRoutingModule],
})
export class AdminModule {}
