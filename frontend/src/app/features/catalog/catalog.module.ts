import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared.module';
import { CatalogRoutingModule } from './catalog-routing.module';
import { BookListPageComponent } from './book-list-page/book-list-page.component';
import { BookDetailPageComponent } from './book-detail-page/book-detail-page.component';

@NgModule({
  declarations: [BookListPageComponent, BookDetailPageComponent],
  imports: [SharedModule, CatalogRoutingModule],
})
export class CatalogModule {}
