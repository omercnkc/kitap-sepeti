import { Directive, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';
import { filterTrPhoneInput } from '../validators/tr-phone';

/** Telefon inputunda harf/özel karakteri engeller; rakam sayısını sınırlar. */
@Directive({
  selector: 'input[appTrPhoneInput]',
})
export class TrPhoneInputDirective {
  constructor(@Optional() @Self() private readonly ngControl: NgControl | null) {}

  @HostListener('input', ['$event'])
  onInput(event: Event): void {
    const el = event.target as HTMLInputElement;
    const filtered = filterTrPhoneInput(el.value);
    if (filtered === el.value) {
      return;
    }

    const start = el.selectionStart;
    el.value = filtered;
    if (this.ngControl?.control) {
      this.ngControl.control.setValue(filtered, { emitEvent: true });
    }
    if (start != null) {
      const pos = Math.min(start, filtered.length);
      el.setSelectionRange(pos, pos);
    }
  }

  @HostListener('paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const el = event.target as HTMLInputElement;
    const pasted = event.clipboardData?.getData('text') ?? '';
    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    const next = filterTrPhoneInput(
      el.value.slice(0, start) + pasted + el.value.slice(end),
    );
    el.value = next;
    if (this.ngControl?.control) {
      this.ngControl.control.setValue(next, { emitEvent: true });
    }
    const pos = Math.min(start + pasted.length, next.length);
    el.setSelectionRange(pos, pos);
  }
}
