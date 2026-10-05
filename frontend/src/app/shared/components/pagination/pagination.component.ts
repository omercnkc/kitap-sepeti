import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';

/**
 * Sayfalama.
 * Backend `page` 0-tabanlı; UI 1-tabanlı — dönüşüm burada.
 * `layout=full`: NgbPagination; `layout=simple`: Önceki / 1/N / Sonraki.
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
  /** `full` = Ngb sayfa numaraları; `simple` = Prev + 1/N + Next */
  @Input() layout: 'full' | 'simple' = 'full';

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
    if (this.layout === 'simple') {
      return this.totalPages >= 1;
    }
    return this.totalPages > 1;
  }

  get canGoPrev(): boolean {
    return this.pageIndex > 0;
  }

  get canGoNext(): boolean {
    return this.totalPages > 0 && this.pageIndex < this.totalPages - 1;
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

  goPrev(): void {
    if (!this.canGoPrev) {
      return;
    }
    this.pageIndexChange.emit(this.pageIndex - 1);
  }

  goNext(): void {
    if (!this.canGoNext) {
      return;
    }
    this.pageIndexChange.emit(this.pageIndex + 1);
  }
}
