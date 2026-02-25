import streamlit as st
import subprocess
import os
import shutil
import sys

# Standard setup
current_dir = os.path.dirname(os.path.abspath(__file__))
sub_env = os.environ.copy()
sub_env["PYTHONIOENCODING"] = "utf-8"

st.set_page_config(page_title="Pose Quality Check", layout="wide")

st.title("🏃‍♂️ Pose Analysis: Visual Quality Check")
st.write("Analyze your video and check the Matplotlib graph before saving.")

# Parameters
st.sidebar.header("Settings")
stability = st.sidebar.slider("Stability", 0.0, 1.0, 0.10)
min_hold = st.sidebar.number_input("Min-Hold", min_value=1, value=7)
confidence = st.sidebar.slider("Confidence", 0.0, 1.0, 0.6)

uploaded_file = st.file_uploader("Upload Video", type=["mp4"])

if uploaded_file:
    # We save the video with its real name
    video_path = os.path.join(current_dir, uploaded_file.name)
    base_name = os.path.splitext(uploaded_file.name)[0]
    
    # These are temporary until you confirm they are "Good"
    temp_json = os.path.join(current_dir, f"temp_{base_name}.json")
    temp_plot = os.path.join(current_dir, f"temp_{base_name}_plot.png")
    
    # Final destination names
    final_json = os.path.join(current_dir, f"{base_name}.json")

    with open(video_path, "wb") as f:
        f.write(uploaded_file.getbuffer())

    if st.button("🔍 Run Analysis & Generate Graph"):
        gen_script = os.path.join(current_dir, "offline_graph_generator_updated.py")
        viz_script = os.path.join(current_dir, "visualize_pose_graph_updated.py")

        with st.status("Crunching data...", expanded=True) as status:
            # 1. Run Generator (Output to Temp JSON)
            gen_cmd = [
                sys.executable, gen_script, video_path,
                "--stability", str(stability), "--min-hold", str(min_hold),
                "--confidence", str(confidence), "--output", temp_json
            ]
            subprocess.run(gen_cmd, capture_output=True, text=True, env=sub_env)
            
            # 2. Run Visualizer (This creates the PNG)
            # We use --no-show so it doesn't get stuck waiting for a popup window
            viz_cmd = [sys.executable, viz_script, temp_json, "--no-show"]
            subprocess.run(viz_cmd, capture_output=True, text=True, env=sub_env)
            
            status.update(label="Graph Generated!", state="complete")

    # --- THE CHECKING AREA ---
    if os.path.exists(temp_json):
        st.divider()
        st.subheader("Visual Inspection")
        
        # Look for the plot image created by the visualizer
        # The visualizer saves it as temp_filename_plot.png
        actual_plot_file = temp_json.replace(".json", "_plot.png")
        
        if os.path.exists(actual_plot_file):
            st.image(actual_plot_file, caption="Check if these pose points are correct")
        else:
            st.error("Graph image not found. Ensure your visualizer script saves the PNG.")

        st.info("If the stick figures and transitions look correct, click Save.")

        col1, col2 = st.columns(2)
        with col1:
            if st.button("✅ Everything is Good - Save JSON"):
                shutil.move(temp_json, final_json)
                st.success(f"Successfully saved as {base_name}.json")
                if os.path.exists(actual_plot_file): os.remove(actual_plot_file)
        
        with col2:
            if st.button("❌ Points are Bad - Discard"):
                os.remove(temp_json)
                if os.path.exists(actual_plot_file): os.remove(actual_plot_file)
                st.rerun()