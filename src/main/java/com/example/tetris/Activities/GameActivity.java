package com.example.tetris.Activities;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.tetris.Fragments.GameOverFragment;
import com.example.tetris.Fragments.PauseFragment;
import com.example.tetris.Models.Tetris;
import com.example.tetris.Models.Tetromino;
import com.example.tetris.R;

public class GameActivity extends AppCompatActivity {
    ImageView ivNextPiece;
    TextView tvScore, tvLines, tvLevel;
    ImageButton rotateLeft, rotateRight, moveLeft, moveRight;
    public Tetris game;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        Handler setImageViewHandler = new Handler(msg -> {
            switch ((Tetromino.Shape) msg.obj) {
                case O: // O-Shape
                    ivNextPiece.setImageResource(R.drawable.o_tetromino);
                    break;
                case I: // I-Shape
                    ivNextPiece.setImageResource(R.drawable.i_tetromino);
                    break;
                case T: // T-Shape
                    ivNextPiece.setImageResource(R.drawable.t_tetromino);
                    break;
                case J: // J-Shape
                    ivNextPiece.setImageResource(R.drawable.j_tetromino);
                    break;
                case L: // L-Shape
                    ivNextPiece.setImageResource(R.drawable.l_tetromino);
                    break;
                case Z: // Z-Shape
                    ivNextPiece.setImageResource(R.drawable.z_tetromino);
                    break;
                case S: // S-Shape
                    ivNextPiece.setImageResource(R.drawable.s_tetromino);
                    break;
            }
            return true;
        });

        Handler setTextViewsHandler = new Handler(msg -> {
            int score = msg.getData().getInt("score"),
                    lines = msg.getData().getInt("lines"),
                    level = msg.getData().getInt("level");
            tvScore.setText(getString(R.string.score, score));
            tvLevel.setText(getString(R.string.level, level));
            tvLines.setText(getString(R.string.lines, lines));
            return true;
        });

        Handler gameOverHandler = new Handler(msg -> {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, GameOverFragment.class, msg.getData())
                    .commit();
            return true;
        });

        Handler gamePauseHandler = new Handler(msg -> {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, PauseFragment.class, null)
                    .commit();
            return true;
        });

        rotateLeft = findViewById(R.id.btnRotateLeft);
        rotateRight = findViewById(R.id.btnRotateRight);

        moveLeft = findViewById(R.id.btnMoveLeft);
        moveRight = findViewById(R.id.btnMoveRight);

        ivNextPiece = findViewById(R.id.ivNextPiece);
        tvLevel = findViewById(R.id.tvLevel);
        tvLines = findViewById(R.id.tvLines);
        tvScore = findViewById(R.id.tvScore);

        game = new Tetris(getApplicationContext(), setImageViewHandler, setTextViewsHandler, gameOverHandler, gamePauseHandler);
        ConstraintLayout layout = findViewById(R.id.layout);
        layout.addView(game, 0);

        rotateLeft.setOnClickListener(game);
        rotateRight.setOnClickListener(game);
        moveLeft.setOnClickListener(game);
        moveRight.setOnClickListener(game);

        // Hiding device navigation
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}
