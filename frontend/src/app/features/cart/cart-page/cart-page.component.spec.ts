import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { CartStore } from '../../../core/cart/cart.store';
import { CartLineResponse, CartResponse } from '../../../core/models';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { SharedModule } from '../../../shared/shared.module';
import { CartPageComponent } from './cart-page.component';

describe('CartPageComponent', () => {
  let fixture: ComponentFixture<CartPageComponent>;
  let cart$: BehaviorSubject<CartResponse | null>;
  let loadSpy: jasmine.Spy;
  let updateSpy: jasmine.Spy;
  let removeSpy: jasmine.Spy;
  let clearSpy: jasmine.Spy;
  let confirmSpy: jasmine.Spy;

  const line: CartLineResponse = {
    bookId: 'b1',
    title: 'Deneme Kitap',
    coverUrl: null,
    quantity: 2,
    currency: 'TRY',
    snapshotUnitPrice: 50,
    currentUnitPrice: 55,
    lineTotal: 110,
    priceChanged: true,
    available: true,
  };

  const cart: CartResponse = {
    catalogStatus: 'VERIFIED',
    currency: 'TRY',
    itemCount: 2,
    lineCount: 1,
    subtotal: 110,
    items: [line],
  };

  beforeEach(async () => {
    cart$ = new BehaviorSubject<CartResponse | null>(cart);
    loadSpy = jasmine.createSpy('load').and.returnValue(of(cart));
    updateSpy = jasmine.createSpy('updateQuantity').and.returnValue(of(cart));
    removeSpy = jasmine.createSpy('remove').and.returnValue(
      of({ ...cart, itemCount: 0, lineCount: 0, items: [], subtotal: 0 }),
    );
    clearSpy = jasmine.createSpy('clear').and.returnValue(
      of({ ...cart, itemCount: 0, lineCount: 0, items: [], subtotal: 0 }),
    );
    confirmSpy = jasmine.createSpy('confirm').and.returnValue(of(true));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, SharedModule],
      declarations: [CartPageComponent],
      providers: [
        {
          provide: CartStore,
          useValue: {
            cart$: cart$.asObservable(),
            load: loadSpy,
            updateQuantity: updateSpy,
            remove: removeSpy,
            clear: clearSpy,
          },
        },
        {
          provide: ConfirmDialogService,
          useValue: { confirm: confirmSpy },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CartPageComponent);
    fixture.detectChanges();
  });

  it('loads cart and renders lines with price-changed badge', () => {
    expect(loadSpy).toHaveBeenCalled();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deneme Kitap');
    expect(el.textContent).toContain('Fiyat değişti');
    expect(el.textContent).toContain('Ara toplam');
  });

  it('shows catalog unavailable banner', () => {
    cart$.next({ ...cart, catalogStatus: 'UNAVAILABLE' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Katalog şu an kullanılamıyor');
    expect(el.textContent).toContain('Deneme Kitap');
  });

  it('shows empty state when cart has no items', () => {
    cart$.next({
      ...cart,
      itemCount: 0,
      lineCount: 0,
      items: [],
      subtotal: 0,
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Sepetiniz boş');
    expect(el.textContent).toContain('Alışverişe devam');
  });

  it('increments quantity via store', () => {
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '.cart-page__qty button',
    );
    (buttons[1] as HTMLButtonElement).click();
    expect(updateSpy).toHaveBeenCalledWith('b1', 3);
  });

  it('removes line without throwing on error', () => {
    removeSpy.and.returnValue(throwError(() => ({ status: 500 })));
    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      'button.btn-outline-danger',
    ) as HTMLButtonElement;
    expect(() => btn.click()).not.toThrow();
    expect(removeSpy).toHaveBeenCalledWith('b1');
  });

  it('clears cart after confirm', () => {
    const btn = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Sepeti boşalt')) as HTMLButtonElement;
    btn.click();
    expect(confirmSpy).toHaveBeenCalled();
    expect(clearSpy).toHaveBeenCalled();
  });

  it('does not clear when confirm is cancelled', () => {
    confirmSpy.and.returnValue(of(false));
    clearSpy.calls.reset();
    const btn = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Sepeti boşalt')) as HTMLButtonElement;
    btn.click();
    expect(clearSpy).not.toHaveBeenCalled();
  });

  it('shows unavailable line warning', () => {
    cart$.next({
      ...cart,
      items: [{ ...line, available: false, priceChanged: false }],
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Satışta değil');
  });
});
