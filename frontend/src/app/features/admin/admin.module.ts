import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { AdminAuthorsPageComponent } from './admin-authors-page/admin-authors-page.component';
import { AdminCategoriesPageComponent } from './admin-categories-page/admin-categories-page.component';
import { AdminNameSlugFormComponent } from './admin-name-slug-form/admin-name-slug-form.component';
import { AdminPlaceholderPageComponent } from './admin-placeholder-page/admin-placeholder-page.component';
import { AdminPublishersPageComponent } from './admin-publishers-page/admin-publishers-page.component';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './admin-shell/admin-shell.component';

@NgModule({
  declarations: [
    AdminShellComponent,
    AdminPlaceholderPageComponent,
    AdminNameSlugFormComponent,
    AdminPublishersPageComponent,
    AdminAuthorsPageComponent,
    AdminCategoriesPageComponent,
  ],
  imports: [SharedModule, AdminRoutingModule],
})
export class AdminModule {}
