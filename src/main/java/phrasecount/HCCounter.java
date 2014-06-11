package phrasecount;

import static phrasecount.Constants.EXPORT_DOC_COUNT_COL;
import static phrasecount.Constants.EXPORT_SUM_COL;
import static phrasecount.Constants.TYPEL;

import java.util.Map.Entry;

import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Range;

import accismus.api.AbstractObserver;
import accismus.api.Column;
import accismus.api.ColumnIterator;
import accismus.api.RowIterator;
import accismus.api.ScannerConfiguration;
import accismus.api.Transaction;
import accismus.api.types.TypedTransaction;

/**
 * This Observer processes high cardinality phrases. It sums up all of the random stat columns that were set.
 */

public class HCCounter extends AbstractObserver {

  @Override
  public void process(Transaction tx, ByteSequence row, Column col) throws Exception {
    TypedTransaction ttx = TYPEL.transaction(tx);

    ScannerConfiguration scanConfig = new ScannerConfiguration();
    scanConfig.setRange(Range.prefix(row.toString(), "stat", "sum:"));

    int sum = sumAndDelete(tx, tx.get(scanConfig));
    int newSum = ttx.get().row(row).col(Constants.STAT_SUM_COL).toInteger(0) + sum;
    ttx.mutate().row(row).col(Constants.STAT_SUM_COL).set(newSum);

    scanConfig.setRange(Range.prefix(row.toString(), "stat", "docCount:"));
    int docCount = sumAndDelete(tx, tx.get(scanConfig));
    int newDocCount = ttx.get().row(row).col(Constants.STAT_DOC_COUNT_COL).toInteger(0) + docCount;
    ttx.mutate().row(row).col(Constants.STAT_DOC_COUNT_COL).set(newDocCount);

    if (ttx.get().row(row).col(Constants.EXPORT_SUM_COL).toInteger() == null) {
      ttx.mutate().row(row).col(EXPORT_SUM_COL).set(newSum);
      ttx.mutate().row(row).col(EXPORT_DOC_COUNT_COL).set(newDocCount);
    }

    ttx.mutate().row(row).col(Constants.EXPORT_CHECK_COL).set();
  }

  private int sumAndDelete(Transaction tx, RowIterator rowIterator) {
    int sum = 0;
    while (rowIterator.hasNext()) {
      Entry<ByteSequence,ColumnIterator> rEntry = rowIterator.next();
      ColumnIterator citer = rEntry.getValue();
      while (citer.hasNext()) {
        Entry<Column,ByteSequence> cEntry = citer.next();
        sum += Integer.parseInt(cEntry.getValue().toString());
        tx.delete(rEntry.getKey(), cEntry.getKey());
      }
    }

    return sum;
  }

}