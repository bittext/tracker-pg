import { Pipe, PipeTransform } from '@angular/core';
import { robinhoodAccountNamed } from './robinhood-account-display';

/** Shows an account suffix with its name: {{ '2835' | rhAccount }} gives "Roth IRA ••••2835". */
@Pipe({ name: 'rhAccount', standalone: true })
export class RhAccountPipe implements PipeTransform {
  transform(suffix: string | null | undefined): string {
    return robinhoodAccountNamed(suffix);
  }
}
