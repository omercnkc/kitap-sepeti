import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { PlaceholderPageComponent } from './layout/placeholder-page/placeholder-page.component';
import { NotFoundComponent } from './layout/not-found/not-found.component';

const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'books' },
      {
        path: 'books',
        component: PlaceholderPageComponent,
        data: { title: 'Kitaplar' },
      },
      {
        path: 'cart',
        component: PlaceholderPageComponent,
        data: { title: 'Sepet' },
      },
      {
        path: 'login',
        component: PlaceholderPageComponent,
        data: { title: 'Giriş' },
      },
      { path: '**', component: NotFoundComponent },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
