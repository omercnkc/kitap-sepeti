import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, NgbModule],
      declarations: [HeaderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show brand and desktop nav links', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kitap Sepeti');
    expect(el.querySelector('a[routerLink="/books"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/cart"]')).toBeTruthy();
    expect(el.querySelector('a[routerLink="/login"]')).toBeTruthy();
    expect(el.querySelector('.navbar-toggler')).toBeTruthy();
  });
});
