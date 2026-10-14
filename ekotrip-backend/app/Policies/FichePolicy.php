<?php

namespace App\Policies;

use App\Models\User;
use Illuminate\Database\Eloquent\Model;

/**
 * Les fiches maîtres proviennent du classeur canonique (IDs stables) :
 * pas de création ni de suppression depuis le back-office ; on archive.
 */
class FichePolicy extends AdminPolicy
{
    public function create(User $user): bool
    {
        return false;
    }

    public function delete(User $user, Model $model): bool
    {
        return false;
    }

    public function deleteAny(User $user): bool
    {
        return false;
    }
}
