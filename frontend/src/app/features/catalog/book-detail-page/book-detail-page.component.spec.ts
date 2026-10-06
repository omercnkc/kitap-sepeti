import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CartStore } from '../../../core/cart/cart.store';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { BookDetailPageComponent } from './book-detail-page.component';

describe('BookDetailPageComponent', () => {
  let fixture: ComponentFixture<BookDetailPageComponent>;
  let httpMock: HttpTestingController;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let router: Router;
  let isLoggedInSpy: jasmine.Spy;
  let isAdminSpy: jasmine.Spy;
  let addSpy: jasmine.Spy;
  let toastSuccessSpy: jasmine.Spy;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: 'b1' }));
    isLoggedInSpy = jasmine.createSpy('isLoggedIn').and.returnValue(false);
    isAdminSpy = jasmine.createSpy('isAdmin').and.returnValue(false);
    addSpy = jasmine.createSpy('add').and.returnValue(of({ itemCount: 1 }));
    toastSuccessSpy = jasmine.createSpy('success');
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule, SharedModule],
      declarations: [BookDetailPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { paramMap: paramMap$.asObservable() },
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: isLoggedInSpy, isAdmin: isAdminSpy },
        },
        {
          provide: CartStore,
          useValue: { add: addSpy },
        },
        {
          provide: ToastService,
          useValue: { success: toastSuccessSpy },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(BookDetailPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows book detail when found', () => {
    httpMock.expectOne('/api/books/b1').flush({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [{ id: 'a1', name: 'Ali', slug: 'ali' }],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [{ id: 'c1', name: 'Roman', slug: 'roman' }],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
      description: 'Açıklama metni',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Detay Kitap');
    expect(el.textContent).toContain('Ali');
    expect(el.textContent).toContain('Açıklama metni');
  });

  it('shows not found on 404', () => {
    httpMock.expectOne('/api/books/b1').flush(
      { title: 'Not Found', status: 404, code: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kitap bulunamadı');
  });

  it('redirects guests to login with returnUrl on add to cart', () => {
    httpMock.expectOne('/api/books/b1').flush({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });
    fixture.detectChanges();

    fixture.componentInstance.onAddToCart({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });

    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ returnUrl: jasmine.any(String) }) }),
    );
    expect(addSpy).not.toHaveBeenCalled();
  });

  it('adds to cart and toasts when logged in', () => {
    isLoggedInSpy.and.returnValue(true);
    httpMock.expectOne('/api/books/b1').flush({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });
    fixture.detectChanges();

    fixture.componentInstance.onAddToCart({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });

    expect(addSpy).toHaveBeenCalledWith('b1');
    expect(toastSuccessSpy).toHaveBeenCalledWith('Sepete eklendi');
  });

  it('hides Sepete ekle for ADMIN and skips cart/toast', () => {
    isAdminSpy.and.returnValue(true);
    isLoggedInSpy.and.returnValue(true);
    httpMock.expectOne('/api/books/b1').flush({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Sepete ekle');

    fixture.componentInstance.onAddToCart({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
    });
    expect(addSpy).not.toHaveBeenCalled();
    expect(toastSuccessSpy).not.toHaveBeenCalled();
  });
});
