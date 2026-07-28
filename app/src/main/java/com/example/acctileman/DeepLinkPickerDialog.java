package com.example.acctileman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Deep Link 选择弹窗：当应用有多个 Deep Link 时弹出，让用户选择具体的 URI。
 */
public class DeepLinkPickerDialog {

    public interface OnDeepLinkSelectedListener {
        void onSelected(AppInfoHelper.DeepLinkItem link);
        void onCancelled();
    }

    private final AlertDialog dialog;
    private OnDeepLinkSelectedListener listener;

    public DeepLinkPickerDialog(Context context, AppInfoHelper.AppItem app,
                                List<AppInfoHelper.DeepLinkItem> links) {
        Logger.d("DeepLinkPicker", "创建弹窗, app=" + app.appName + " Deep Link 数=" + links.size());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 16);

        // 提示文字
        TextView tvHint = new TextView(context);
        tvHint.setText("检测到多个 Deep Link，请选择启动页面:");
        tvHint.setTextSize(14);
        tvHint.setTextColor(0xFF666666);
        tvHint.setPadding(0, 0, 0, 24);
        root.addView(tvHint);

        // 列表
        ListView listView = new ListView(context);
        final DeepLinkListAdapter adapter = new DeepLinkListAdapter(links);
        listView.setAdapter(adapter);

        int itemHeight = (int) (80 * context.getResources().getDisplayMetrics().density);
        int listHeight = Math.min(links.size(), 6) * itemHeight;
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight));
        root.addView(listView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(app.appName + " - 选择启动页面");
        builder.setView(root);
        builder.setNegativeButton("跳过", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Logger.d("DeepLinkPicker", "用户跳过");
                if (listener != null) {
                    listener.onCancelled();
                }
            }
        });

        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppInfoHelper.DeepLinkItem selected = (AppInfoHelper.DeepLinkItem) adapter.getItem(position);
                Logger.d("DeepLinkPicker", "选择: " + selected.uri);
                dialog.dismiss();
                if (listener != null) {
                    listener.onSelected(selected);
                }
            }
        });
    }

    public void setListener(OnDeepLinkSelectedListener l) {
        this.listener = l;
    }

    public void show() {
        dialog.show();
    }

    // ---- Adapter ----

    private static class DeepLinkListAdapter extends BaseAdapter {
        private List<AppInfoHelper.DeepLinkItem> items;

        DeepLinkListAdapter(List<AppInfoHelper.DeepLinkItem> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = createItemView(parent);
            }
            bindItemView(convertView, items.get(position));
            return convertView;
        }

        private View createItemView(ViewGroup parent) {
            Context ctx = parent.getContext();
            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(32, 24, 32, 24);
            layout.setMinimumHeight(64);

            TextView tvUri = new TextView(ctx);
            tvUri.setId(android.R.id.text1);
            tvUri.setTextSize(15);
            tvUri.setTextColor(0xFF1565C0);
            layout.addView(tvUri);

            TextView tvActivity = new TextView(ctx);
            tvActivity.setId(android.R.id.text2);
            tvActivity.setTextSize(12);
            tvActivity.setTextColor(0xFF888888);
            layout.addView(tvActivity);

            return layout;
        }

        private void bindItemView(View view, AppInfoHelper.DeepLinkItem item) {
            TextView tvUri = view.findViewById(android.R.id.text1);
            TextView tvActivity = view.findViewById(android.R.id.text2);

            if (tvUri != null) {
                tvUri.setText(item.uri);
            }
            if (tvActivity != null) {
                String desc = "";
                if (item.host != null && !item.host.isEmpty()) {
                    desc = "Host: " + item.host;
                }
                if (item.activityName != null && !item.activityName.isEmpty()) {
                    if (!desc.isEmpty()) desc += "  ";
                    desc += "Activity: " + shortName(item.activityName);
                }
                tvActivity.setText(desc.isEmpty() ? "(无额外信息)" : desc);
            }
        }

        private String shortName(String fullName) {
            int lastDot = fullName.lastIndexOf('.');
            if (lastDot >= 0 && lastDot + 1 < fullName.length()) {
                return fullName.substring(lastDot + 1);
            }
            return fullName;
        }
    }
}
