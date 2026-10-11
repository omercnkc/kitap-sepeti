import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { CatalogApi } from '../../../core/api/catalog.api';
import { AuthService } from '../../../core/auth/auth.service';
import { BookSummary, PageResponse } from '../../../core/models';
import { BookCarouselComponent } from './book-carousel.component';

describe('BookCarouselComponent', () => {
  let fixture: ComponentFixture<BookCarouselComponent>;
  let listSpy: jasmine.Spy;
  let isAdminSpy: jasmine.Spy;

  const books: BookSummary[] = [
    {
      id: 'b1',
      title: 'Kitap Bir',
      authors: [{ id: 'a1', name: 'Yazar', slug: 'yazar' }],
      priceAmount: 100,
      currency: 'TRY',
      inStock: true,
    },
    {
      id: 'b2',
      title: 'Kitap İki',
      authors: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    },
  ];

  beforeEach(async () => {
    isAdminSpy = jasmine.createSpy('isAdmin').and.returnValue(false);
    listSpy = jasmine.createSpy('list').and.returnValue(
      of({
        items: books,
        page: 0,
        size: 12,
        totalElements: 2,
        totalPages: 1,
      } as PageResponse<BookSummary>),
    );

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [BookCarouselComponent],
      providers: [
        { provide: CatalogApi, useValue: { list: listSpy } },
        { provide: AuthService, useValue: { isAdmin: isAdminSpy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookCarouselComponent);
  });

  it('loads newest books and renders carousel', () => {
    fixture.detectChanges();
    expect(listSpy).toHaveBeenCalledWith({ sort: 'newest', page: 0, size: 12 });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.book-carousel')).toBeTruthy();
    expect(el.textContent).toContain('Kitap Bir');
    expect(el.querySelectorAll('.book-carousel__dot').length).toBe(2);
  });

  it('hides when API fails', () => {
    listSpy.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.book-carousel')).toBeFalsy();
  });

  it('next advances active index', () => {
    fixture.detectChanges();
    const cmp = fixture.componentInstance;
    cmp.next();
    expect(cmp.activeIndex).toBe(1);
    cmp.next();
    expect(cmp.activeIndex).toBe(0);
  });

  it('shows cart icon for ADMIN but does not emit', () => {
    isAdminSpy.and.returnValue(true);
    fixture.detectChanges();
    const cmp = fixture.componentInstance;
    const spy = jasmine.createSpy('addToCart');
    cmp.addToCart.subscribe(spy);
    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '.book-carousel__cart-btn',
    ) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    btn.click();
    expect(spy).not.toHaveBeenCalled();
  });
});
