import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminPlaceholderPageComponent } from './admin-placeholder-page/admin-placeholder-page.component';
import { AdminShellComponent } from './admin-shell/admin-shell.component';

const routes: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'publishers' },
      {
        path: 'publishers',
        component: AdminPlaceholderPageComponent,
        data: { title: 'Yayınevleri' },
      },
      {
        path: 'authors',
        component: AdminPlaceholderPageComponent,
        data: { title: 'Yazarlar' },
      },
      {
        path: 'categories',
        component: AdminPlaceholderPageComponent,
        data: { title: 'Kategoriler' },
      },
      {
        path: 'books',
        component: AdminPlaceholderPageComponent,
        data: { title: 'Kitaplar' },
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
