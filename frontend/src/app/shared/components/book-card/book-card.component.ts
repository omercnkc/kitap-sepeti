import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { BookSummary } from '../../../core/models';

@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.component.html',
  styleUrls: ['./book-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookCardComponent {
  @Input() book!: BookSummary;
  @Output() readonly addToCart = new EventEmitter<BookSummary>();

  constructor(private readonly auth: AuthService) {}

  authorsLabel(book: BookSummary): string {
    if (!book.authors || book.authors.length === 0) {
      return '';
    }
    return book.authors.map((a) => a.name).join(', ');
  }

  onAddToCart(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.book.inStock || this.auth.isAdmin()) {
      return;
    }
    this.addToCart.emit(this.book);
  }
}
