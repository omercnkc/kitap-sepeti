import { LOCALE_ID, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CoreModule } from './core/core.module';
import { LayoutModule } from './layout/layout.module';

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, CoreModule.forRoot(), LayoutModule, AppRoutingModule],
  providers: [{ provide: LOCALE_ID, useValue: 'tr-TR' }],
  bootstrap: [AppComponent],
})
export class AppModule {}
