package cn.garymb.ygomobile.ui.mycard.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.DeckMatchupStats;

public class DeckMatchupTableAdapter extends RecyclerView.Adapter<DeckMatchupTableAdapter.TableViewHolder> {

    public enum DisplayMode {
        FIRST_GO,
        SECOND_GO,
        TOTAL
    }

    private List<DeckMatchupStats> deckStatsList;
    private List<String> allOpponentDecks;
    private DisplayMode displayMode;

    public DeckMatchupTableAdapter(List<DeckMatchupStats> deckStatsList, List<String> allOpponentDecks, DisplayMode mode) {
        this.deckStatsList = deckStatsList;
        this.allOpponentDecks = allOpponentDecks;
        this.displayMode = mode;
    }

    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_matchup_table_row, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        DeckMatchupStats stats = deckStatsList.get(position);
        holder.tvDeckName.setText(stats.getDeckName());

        holder.llWinRateCells.removeAllViews();

        for (String opponentDeck : allOpponentDecks) {
            DeckMatchupStats.MatchStats matchStats = getMatchStats(stats, opponentDeck);
            TextView cell = createCell(holder.itemView.getContext(), matchStats);
            holder.llWinRateCells.addView(cell);
        }
    }

    private DeckMatchupStats.MatchStats getMatchStats(DeckMatchupStats stats, String opponentDeck) {
        switch (displayMode) {
            case FIRST_GO:
                return stats.getFirstGoMatchup(opponentDeck);
            case SECOND_GO:
                return stats.getSecondGoMatchup(opponentDeck);
            case TOTAL:
            default:
                return stats.getTotalMatchup(opponentDeck);
        }
    }

    private TextView createCell(android.content.Context context, DeckMatchupStats.MatchStats stats) {
        TextView cell = (TextView) LayoutInflater.from(context)
                .inflate(R.layout.item_win_rate_cell, null);

        if (stats == null || stats.getTotalMatches() == 0) {
            cell.setText("N/A");
            cell.setTextColor(Color.GRAY);
        } else {
            float winRate = stats.getWinRate();
            cell.setText(String.format("%.0f%%", winRate));

            if (winRate >= 60) {
                cell.setTextColor(Color.parseColor("#00FF00"));
            } else if (winRate >= 50) {
                cell.setTextColor(Color.parseColor("#FFFF00"));
            } else if (winRate >= 40) {
                cell.setTextColor(Color.parseColor("#FFA500"));
            } else {
                cell.setTextColor(Color.parseColor("#FF4444"));
            }
        }

        return cell;
    }

    @Override
    public int getItemCount() {
        return deckStatsList == null ? 0 : deckStatsList.size();
    }

    static class TableViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeckName;
        LinearLayout llWinRateCells;

        TableViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeckName = itemView.findViewById(R.id.tv_deck_name);
            llWinRateCells = itemView.findViewById(R.id.ll_win_rate_cells);
        }
    }
}
