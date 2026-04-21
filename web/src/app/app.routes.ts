import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { AdminComponent } from './pages/admin/admin.component';
import { ExerciseComponent } from './pages/exercise/exercise.component';
import { FinanceComponent } from './pages/finance/finance.component';
import { ManagementComponent } from './pages/management/management.component';
import { ReportsComponent } from './pages/reports/reports.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'exercise' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'exercise',
    canActivate: [authGuard],
    component: ExerciseComponent,
  },
  {
    path: 'finance',
    canActivate: [authGuard],
    component: FinanceComponent,
  },
  {
    path: 'management',
    canActivate: [authGuard],
    component: ManagementComponent,
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    component: ReportsComponent,
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    component: AdminComponent,
  },
  {
    path: 'logs',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/logs/logs.component').then((m) => m.LogsComponent),
  },
  { path: '**', redirectTo: 'exercise' },
];
