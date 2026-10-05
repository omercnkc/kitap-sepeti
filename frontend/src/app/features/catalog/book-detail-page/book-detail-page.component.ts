import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-book-detail-page',
  templateUrl: './book-detail-page.component.html',
  styleUrls: ['./book-detail-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookDetailPageComponent {
  readonly bookId: string | null;

  constructor(private readonly route: ActivatedRoute) {
    this.bookId = this.route.snapshot.paramMap.get('id');
  }
}
