import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-book-list-page',
  templateUrl: './book-list-page.component.html',
  styleUrls: ['./book-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookListPageComponent {}
