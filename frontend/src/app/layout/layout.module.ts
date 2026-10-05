import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { ShellComponent } from './shell/shell.component';
import { HeaderComponent } from './header/header.component';
import { FooterComponent } from './footer/footer.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { PlaceholderPageComponent } from './placeholder-page/placeholder-page.component';

@NgModule({
  declarations: [
    ShellComponent,
    HeaderComponent,
    FooterComponent,
    NotFoundComponent,
    PlaceholderPageComponent,
  ],
  imports: [SharedModule],
  exports: [ShellComponent, NotFoundComponent, PlaceholderPageComponent],
})
export class LayoutModule {}
