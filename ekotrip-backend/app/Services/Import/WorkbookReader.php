<?php

namespace App\Services\Import;

use OpenSpout\Reader\XLSX\Options;
use OpenSpout\Reader\XLSX\Reader;
use RuntimeException;

/**
 * Lecture en flux du classeur canonique (OpenSpout).
 */
class WorkbookReader
{
    /**
     * @param  list<string>  $sheetNames
     * @return array<string, list<list<mixed>>> lignes brutes (en-tête incluse) par onglet
     */
    public function read(string $path, array $sheetNames): array
    {
        if (! is_file($path)) {
            throw new RuntimeException("Classeur introuvable : {$path}");
        }

        $options = new Options;
        $options->SHOULD_FORMAT_DATES = false;
        $options->SHOULD_PRESERVE_EMPTY_ROWS = false;

        $reader = new Reader($options);
        $reader->open($path);

        $sheets = [];
        try {
            foreach ($reader->getSheetIterator() as $sheet) {
                if (! in_array($sheet->getName(), $sheetNames, true)) {
                    continue;
                }
                $rows = [];
                foreach ($sheet->getRowIterator() as $row) {
                    $values = $row->toArray();
                    if (array_filter($values, fn ($v) => $v !== null && $v !== '') === []) {
                        continue;
                    }
                    $rows[] = $values;
                }
                $sheets[$sheet->getName()] = $rows;
            }
        } finally {
            $reader->close();
        }

        foreach ($sheetNames as $name) {
            if (! array_key_exists($name, $sheets)) {
                throw new RuntimeException("Onglet manquant dans le classeur : {$name}");
            }
        }

        return $sheets;
    }

    /**
     * Transforme des lignes brutes en tableaux associatifs selon la première ligne.
     *
     * @param  list<list<mixed>>  $rows
     * @return list<array<string,mixed>>
     */
    public static function associate(array $rows): array
    {
        $header = array_map(fn ($h) => is_string($h) ? trim($h) : $h, array_shift($rows) ?? []);
        $out = [];
        foreach ($rows as $index => $row) {
            $assoc = ['__row' => $index + 2];
            foreach ($header as $i => $name) {
                if ($name !== null && $name !== '') {
                    $assoc[$name] = $row[$i] ?? null;
                }
            }
            $out[] = $assoc;
        }

        return $out;
    }
}
