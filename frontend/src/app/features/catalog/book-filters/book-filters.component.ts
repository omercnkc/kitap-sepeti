import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { BookFilter, CategoryTree } from '../../../core/models';

export const SORT_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'newest', label: 'En yeni' },
  { value: 'price_asc', label: 'Fiyat (artan)' },
  { value: 'price_desc', label: 'Fiyat (azalan)' },
  { value: 'title_asc', label: 'Başlık (A–Z)' },
];

export interface BookFilterChange {
  categoryId?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  sort?: string | null;
}

@Component({
  selector: 'app-book-filters',
  templateUrl: './book-filters.component.html',
  styleUrls: ['./book-filters.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookFiltersComponent implements OnChanges {
  readonly sortOptions = SORT_OPTIONS;

  @Input() categories: CategoryTree[] = [];
  @Input() filter: BookFilter = {};
  @Input() priceRangeError: string | null = null;

  @Output() readonly filterChange = new EventEmitter<BookFilterChange>();
  @Output() readonly clear = new EventEmitter<void>();

  draftMinPrice = '';
  draftMaxPrice = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['filter']) {
      this.draftMinPrice =
        this.filter.minPrice !== null && this.filter.minPrice !== undefined
          ? String(this.filter.minPrice)
          : '';
      this.draftMaxPrice =
        this.filter.maxPrice !== null && this.filter.maxPrice !== undefined
          ? String(this.filter.maxPrice)
          : '';
    }
  }

  onCategorySelect(categoryId: string | null): void {
    this.filterChange.emit({ categoryId });
  }

  onSortChange(sort: string): void {
    this.filterChange.emit({ sort: sort || null });
  }

  onApplyPrice(): void {
    const minPrice = parseOptionalNumber(this.draftMinPrice);
    const maxPrice = parseOptionalNumber(this.draftMaxPrice);
    this.filterChange.emit({ minPrice, maxPrice });
  }

  onClear(): void {
    this.clear.emit();
  }

  isCategorySelected(id: string): boolean {
    return this.filter.categoryId === id;
  }
}

function parseOptionalNumber(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed === '') {
    return null;
  }
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : null;
}
