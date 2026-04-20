import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'exercise' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'exercise',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/exercise/exercise.component').then((m) => m.ExerciseComponent),
  },
  {
    path: 'finance',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/finance/finance.component').then((m) => m.FinanceComponent),
  },
  {
    path: 'management',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/management/management.component').then((m) => m.ManagementComponent),
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/reports/reports.component').then((m) => m.ReportsComponent),
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/admin/admin.component').then((m) => m.AdminComponent),
  },
  {
    path: 'logs',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/logs/logs.component').then((m) => m.LogsComponent),
  },
  { path: '**', redirectTo: 'exercise' },
];
