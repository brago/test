<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;

class CreateAdmin extends Command
{
    protected $signature = 'ekotrip:create-admin {email} {--name=Administrateur}';

    protected $description = 'Crée (ou promeut) un administrateur du back-office';

    public function handle(): int
    {
        $email = (string) $this->argument('email');
        if (Validator::make(['email' => $email], ['email' => 'required|email'])->fails()) {
            $this->error('Adresse e-mail invalide.');

            return self::FAILURE;
        }

        $user = User::query()->firstOrNew(['email' => $email]);
        if (! $user->exists) {
            $password = (string) $this->secret('Mot de passe (12 caractères minimum)');
            if (mb_strlen($password) < 12) {
                $this->error('Mot de passe trop court.');

                return self::FAILURE;
            }
            $user->name = (string) $this->option('name');
            $user->password = $password;
        }
        $user->forceFill(['is_admin' => true])->save();

        $this->info("Administrateur prêt : {$email}");

        return self::SUCCESS;
    }
}
