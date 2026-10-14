<?php

namespace App\Policies;

use App\Models\User;
use Illuminate\Database\Eloquent\Model;

/**
 * Phase 1 : le back-office est réservé aux administrateurs ÉKO TRIP.
 * Les policies spécifiques restreignent ensuite les actions (création, suppression…).
 */
abstract class AdminPolicy
{
    protected function admin(User $user): bool
    {
        return (bool) $user->is_admin;
    }

    public function viewAny(User $user): bool
    {
        return $this->admin($user);
    }

    public function view(User $user, Model $model): bool
    {
        return $this->admin($user);
    }

    public function create(User $user): bool
    {
        return $this->admin($user);
    }

    public function update(User $user, Model $model): bool
    {
        return $this->admin($user);
    }

    public function delete(User $user, Model $model): bool
    {
        return $this->admin($user);
    }

    public function deleteAny(User $user): bool
    {
        return $this->admin($user);
    }
}
