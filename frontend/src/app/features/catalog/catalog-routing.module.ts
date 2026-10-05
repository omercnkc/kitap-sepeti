import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BookListPageComponent } from './book-list-page/book-list-page.component';
import { BookDetailPageComponent } from './book-detail-page/book-detail-page.component';

const routes: Routes = [
  { path: '', component: BookListPageComponent },
  { path: ':id', component: BookDetailPageComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CatalogRoutingModule {}
