import {
  ModuleWithProviders,
  NgModule,
  Optional,
  SkipSelf,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { ErrorInterceptor } from './interceptors/error.interceptor';

export function throwIfAlreadyLoaded(parentModule: unknown, moduleName: string): void {
  if (parentModule) {
    throw new Error(
      `${moduleName} already loaded. Import it only once via AppModule (CoreModule.forRoot()).`,
    );
  }
}

@NgModule({
  imports: [CommonModule, HttpClientModule],
})
export class CoreModule {
  constructor(@Optional() @SkipSelf() parentModule: CoreModule) {
    throwIfAlreadyLoaded(parentModule, 'CoreModule');
  }

  static forRoot(): ModuleWithProviders<CoreModule> {
    return {
      ngModule: CoreModule,
      providers: [
        // AuthInterceptor (UI-3) eklendiğinde bu satırın ÜSTÜNE konur.
        { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
      ],
    };
  }
}
