import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BookSummary } from '../../../core/models';
import { BookCardComponent } from './book-card.component';

describe('BookCardComponent', () => {
  let fixture: ComponentFixture<BookCardComponent>;
  let component: BookCardComponent;

  const book: BookSummary = {
    id: 'b1',
    title: 'Deneme Kitap',
    authors: [{ id: 'a1', name: 'Ali Yazar', slug: 'ali-yazar' }],
    publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
    priceAmount: 120,
    currency: 'TRY',
    inStock: true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommonModule, RouterTestingModule],
      declarations: [BookCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookCardComponent);
    component = fixture.componentInstance;
    component.book = book;
    fixture.detectChanges();
  });

  it('renders title and price', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deneme Kitap');
    expect(el.textContent).toContain('120');
  });

  it('shows Tükendi when out of stock and does not emit addToCart', () => {
    fixture = TestBed.createComponent(BookCardComponent);
    component = fixture.componentInstance;
    component.book = { ...book, inStock: false };
    fixture.detectChanges();

    const spy = jasmine.createSpy('addToCart');
    component.addToCart.subscribe(spy);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Tükendi');
    const btn = el.querySelector('button') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    btn.click();
    expect(spy).not.toHaveBeenCalled();
  });

  it('emits addToCart when in stock', () => {
    const spy = jasmine.createSpy('addToCart');
    component.addToCart.subscribe(spy);
    const btn = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
    btn.click();
    expect(spy).toHaveBeenCalledWith(book);
  });
});
