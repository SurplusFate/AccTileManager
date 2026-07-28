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
 * 服务选择弹窗：当应用有多个无障碍服务时弹出，让用户选择具体的服务。
 * 基于 AlertDialog 实现，避免 Dialog 的 setButton 兼容性问题。
 */
public class ServicePickerDialog {

    public interface OnServiceSelectedListener {
        void onSelected(AppInfoHelper.ServiceItem service);
        void onCancelled();
    }

    private final AlertDialog dialog;
    private OnServiceSelectedListener listener;

    public ServicePickerDialog(Context context, AppInfoHelper.AppItem app,
                               List<AppInfoHelper.ServiceItem> services) {
        Logger.d("ServicePicker", "创建弹窗, app=" + app.appName + " 服务数=" + services.size());

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 16);

        // 提示文字
        TextView tvHint = new TextView(context);
        tvHint.setText("检测到多个无障碍服务，请选择:");
        tvHint.setTextSize(14);
        tvHint.setTextColor(0xFF666666);
        tvHint.setPadding(0, 0, 0, 24);
        root.addView(tvHint);

        // 服务列表
        ListView listView = new ListView(context);
        final ServiceListAdapter adapter = new ServiceListAdapter(services);
        listView.setAdapter(adapter);

        // 让 ListView 有合适的高度
        int itemHeight = (int) (72 * context.getResources().getDisplayMetrics().density);
        int listHeight = Math.min(services.size(), 5) * itemHeight;
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight));
        root.addView(listView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(app.appName + " - 选择无障碍服务");
        builder.setView(root);
        builder.setNegativeButton("跳过", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Logger.d("ServicePicker", "用户跳过");
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
                AppInfoHelper.ServiceItem selected = (AppInfoHelper.ServiceItem) adapter.getItem(position);
                Logger.d("ServicePicker", "选择: " + selected.component);
                dialog.dismiss();
                if (listener != null) {
                    listener.onSelected(selected);
                }
            }
        });
    }

    public void setListener(OnServiceSelectedListener l) {
        this.listener = l;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    // ---- Adapter ----

    private static class ServiceListAdapter extends BaseAdapter {
        private List<AppInfoHelper.ServiceItem> items;

        ServiceListAdapter(List<AppInfoHelper.ServiceItem> items) {
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

            TextView tvName = new TextView(ctx);
            tvName.setId(android.R.id.text1);
            tvName.setTextSize(15);
            tvName.setTextColor(0xFF000000);
            layout.addView(tvName);

            TextView tvDesc = new TextView(ctx);
            tvDesc.setId(android.R.id.text2);
            tvDesc.setTextSize(12);
            tvDesc.setTextColor(0xFF888888);
            layout.addView(tvDesc);

            TextView tvComponent = new TextView(ctx);
            tvComponent.setId(android.R.id.hint);
            tvComponent.setTextSize(11);
            tvComponent.setTextColor(0xFFAAAAAA);
            tvComponent.setPadding(0, 8, 0, 0);
            layout.addView(tvComponent);

            return layout;
        }

        private void bindItemView(View view, AppInfoHelper.ServiceItem item) {
            TextView tvName = view.findViewById(android.R.id.text1);
            TextView tvDesc = view.findViewById(android.R.id.text2);
            TextView tvComponent = view.findViewById(android.R.id.hint);

            // 从类名提取简短名称
            String shortName = item.className;
            int lastDot = shortName.lastIndexOf('.');
            if (lastDot >= 0) {
                shortName = shortName.substring(lastDot + 1);
            }

            if (tvName != null) {
                tvName.setText((item.enabled ? "[已启用] " : "") + shortName);
                if (item.enabled) {
                    tvName.setTextColor(0xFF4CAF50);
                }
            }
            if (tvDesc != null) {
                tvDesc.setText(item.description != null && !item.description.isEmpty()
                        ? item.description : "(无描述)");
            }
            if (tvComponent != null) {
                tvComponent.setText(item.component);
            }
        }
    }
}
