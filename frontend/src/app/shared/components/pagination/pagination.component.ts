import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';

/**
 * NgbPagination sarmalayıcı.
 * Backend `page` 0-tabanlı; UI 1-tabanlı — dönüşüm burada.
 */
@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.component.html',
  styleUrls: ['./pagination.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  /** Backend page index (0-based). */
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() totalElements = 0;
  @Input() totalPages = 0;
  @Input() maxSize = 5;

  /** Emits 0-based page index. */
  @Output() readonly pageIndexChange = new EventEmitter<number>();

  get uiPage(): number {
    return this.pageIndex + 1;
  }

  get collectionSize(): number {
    if (this.totalElements > 0) {
      return this.totalElements;
    }
    return Math.max(this.totalPages, 0) * Math.max(this.pageSize, 1);
  }

  get visible(): boolean {
    return this.totalPages > 1;
  }

  onPageChange(uiPage: number): void {
    const nextIndex = Math.max(0, uiPage - 1);
    if (nextIndex === this.pageIndex) {
      return;
    }
    if (this.totalPages > 0 && nextIndex >= this.totalPages) {
      return;
    }
    this.pageIndexChange.emit(nextIndex);
  }
}
